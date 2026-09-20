package app.lightmove.api.enrichment.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingCompanyEnricher;
import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What a provider said about a LinkedIn page is bought once for the whole platform, not once per
 * mandate — the bill this table exists to stop.
 *
 * <p>Read through JDBC because nothing exposes the cache: it is the enrichment worker's own store and
 * no endpoint answers from it.
 */
@IntegrationTest
class VendorCompanyCacheIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;
    @Autowired private RecordingCompanyEnricher companyEnricher;

    private String adminToken;
    private String clientId;

    @BeforeEach
    void freshUniverse() {
        new ApolloUniverse(db).reset();
    }

    @Test
    @DisplayName("a second mandate capturing the same company costs no vendor call")
    void theSecondMandatePaysNothing() throws Exception {
        firm();
        companyEnricher.answerWith(sampleCo());
        String first = mandate("Head of Retail");
        String second = mandate("Group CFO");

        capture(first, "SampleCo", "https://www.linkedin.com/company/sampleco/");
        capture(second, "SampleCo", "https://uk.linkedin.com/company/SampleCo");

        // One lookup for two mandates. The slug is lower-cased and host-agnostic, so the second
        // capture asks about the row the first one paid for.
        assertThat(companyEnricher.fetchedSlugs()).containsExactly("sampleco");
        // Both rows still carry the research — a cache hit fills the same fields a fresh call would.
        assertThat(triagedIndustry(first)).isEqualTo("computer software");
        assertThat(triagedIndustry(second)).isEqualTo("computer software");
    }

    @Test
    @DisplayName("the row keeps the vendor's own leaf, what it resolves to, and the payload")
    void theRowKeepsBothVocabularies() throws Exception {
        firm();
        companyEnricher.answerWith(sampleCo());

        capture(mandate("Head of Retail"), "SampleCo", "https://www.linkedin.com/company/sampleco/");

        Map<String, Object> cached = db.queryForMap("""
                SELECT provider, found, company_name, industry_v2_code, industry_v2_label,
                       industry_v1, sector_group, employees_linkedin, keywords, raw
                FROM app_lm_company WHERE linkedin_slug = 'sampleco'
                """);
        assertThat(cached).containsEntry("provider", "recording").containsEntry("found", true);
        // The one place in the schema where industry_v2_* is finer data rather than V1 renamed.
        assertThat(cached).containsEntry("industry_v2_label", "Software Development")
                .containsEntry("industry_v2_code", 4)
                .containsEntry("industry_v1", "computer software")
                .containsEntry("sector_group", "Technology");
        // Not num_employees: this counts profiles claiming the employer.
        assertThat(cached).containsEntry("employees_linkedin", 841);
        String[] keywords = db.queryForObject(
                "SELECT keywords FROM app_lm_company WHERE linkedin_slug = 'sampleco'",
                (row, number) -> (String[]) row.getArray(1).getArray());
        assertThat(keywords).containsExactly("insurance software", "insurance platform");
        // jsonb reads back as a PGobject, so the payload is compared as the text Postgres holds.
        assertThat(cached.get("raw")).hasToString("{\"id\": \"sampleco\", \"name\": \"SampleCo\"}");
    }

    @Test
    @DisplayName("a slug the provider does not carry is remembered, and never re-bought")
    void aMissIsRemembered() throws Exception {
        firm();

        capture(mandate("Head of Retail"), "Ghost Holding",
                "https://www.linkedin.com/company/ghost-holding/");
        capture(mandate("Group CFO"), "Ghost Holding",
                "https://www.linkedin.com/company/ghost-holding/");

        assertThat(companyEnricher.fetchedSlugs()).containsExactly("ghost-holding");
        // Stored as a miss and nothing else: without the row, every mandate re-buys the same nothing.
        assertThat(db.queryForMap("""
                SELECT found, company_name FROM app_lm_company WHERE linkedin_slug = 'ghost-holding'
                """)).containsEntry("found", false).containsEntry("company_name", null);
    }

    @Test
    @DisplayName("a LinkedIn search URL is not a company page and never becomes a row")
    void aSearchUrlIsNeverCached() throws Exception {
        firm();

        // What CandidateService.mapToEmployer writes when a researched employer has no company page.
        capture(mandate("Head of Retail"), "Al Fahim Group",
                "https://www.linkedin.com/search/results/all/?keywords=Al+Fahim+Group");

        assertThat(companyEnricher.fetchedSlugs()).isEmpty();
        assertThat(db.queryForObject("SELECT count(*) FROM app_lm_company", Integer.class)).isZero();
    }

    @Test
    @DisplayName("the cache names no workspace, project or user")
    void theCacheIsNotTenantScoped() {
        // The boundary in V64's header, asserted rather than trusted: a column added here later would
        // put one firm's research in a table every other firm reads.
        assertThat(db.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'app_lm_company'
                """, String.class))
                .isNotEmpty()
                .noneMatch(column -> column.contains("workspace") || column.contains("project")
                        || column.contains("added_by") || column.contains("user"));
    }

    private static VendorCompanyRecord sampleCo() {
        return new VendorCompanyRecord("sampleco", "SampleCo", "Software Development", "Ireland",
                "Dublin", 841, "https://www.sampleco.example/",
                "https://www.linkedin.com/company/sampleco", 1993,
                "SampleCo is a leading provider of core systems.",
                "https://media.example.com/sampleco-logo.png",
                List.of("insurance software", "insurance platform"),
                "{\"id\":\"sampleco\",\"name\":\"SampleCo\"}");
    }

    private void capture(String projectId, String companyName, String linkedinUrl) throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/triage/capture")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"%s","source":"extension","companyLinkedinUrl":"%s"}
                                """.formatted(companyName, linkedinUrl)))
                .andExpect(status().isCreated());
    }

    private String triagedIndustry(String projectId) {
        return db.queryForObject("""
                SELECT industry FROM app_lm_project_triage_company WHERE project_id = ?::uuid
                """, String.class, projectId);
    }

    /** One firm, so both mandates below live in the same workspace and neither shares a company row. */
    private void firm() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Vendor Cache Firm");
        adminToken = login(alok);
        clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Cache Client"}"""))
                .andReturn()).get("id").asText();
    }

    private String mandate(String positionTitle) throws Exception {
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s"}
                                """.formatted(clientId, positionTitle)))
                .andReturn()).get("id").asText();
    }
}
