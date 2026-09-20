package app.lightmove.api.triagecompany;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A triaged company's V2 name and sector are derived, denormalised and never written on their own.
 * Two doors reach the same table by different code — the entity for a capture, one multi-row
 * statement for a bulk add — so what this checks is that both arrive at the same four values.
 *
 * <p>Read through JDBC rather than the API because nothing exposes these columns yet: they exist for
 * the report's rollup and for SQL, and a DTO field nobody reads would be scope this issue does not
 * have.
 */
@IntegrationTest
class RowIndustryFormsIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;

    private ApolloUniverse universe;
    private String adminToken;

    @BeforeEach
    void freshUniverse() {
        universe = new ApolloUniverse(db);
        universe.reset();
    }

    @Test
    @DisplayName("a capture in the vendor's vocabulary files under the universe's, with its sector")
    void aCaptureCarriesEveryForm() throws Exception {
        String projectId = mandate("Derived Forms Firm");

        mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"SampleCo","source":"extension",
                                 "industry":"Software Development"}"""))
                .andExpect(status().isCreated());

        assertFiledAs(projectId, "SampleCo", "computer software", 4, "Software Development", "Technology");
    }

    @Test
    @DisplayName("a bulk add from the market derives the same forms the entity would")
    void aBulkAddCarriesEveryForm() throws Exception {
        String projectId = mandate("Bulk Forms Firm");
        universe.company("a1", "ACWA Power").industry("oil & energy").employees(10).insert();

        mvc.perform(post("/api/v1/projects/" + projectId + "/triage/bulk")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apolloAccountIds":["a1"],"status":"inUniverse"}"""))
                .andExpect(status().isOk());

        // Written by TriageCompanyWriter's one multi-row INSERT, not by the entity — the path that
        // would silently skip the derived columns if they were set anywhere but Industries.resolve.
        assertFiledAs(projectId, "ACWA Power", "oil & energy", 57, "Oil and Gas", "Energy & Utilities");
    }

    @Test
    @DisplayName("an industry nobody can resolve keeps itself and derives nothing")
    void anUnresolvableIndustryDerivesNothing() throws Exception {
        String projectId = mandate("Unresolvable Forms Firm");

        mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Majlis Co","source":"manual",
                                 "industry":"regional majlis catering"}"""))
                .andExpect(status().isCreated());

        // Null here is "nobody could resolve this", which the report counts apart from a sector.
        Map<String, Object> row = row(projectId, "Majlis Co");
        assertThat(row.get("industry")).isEqualTo("regional majlis catering");
        assertThat(row.get("industry_v2_code")).isNull();
        assertThat(row.get("industry_v2_label")).isNull();
        assertThat(row.get("sector_group")).isNull();
    }

    /** Scoped to the mandate: the suite shares one database and other flows use these company names. */
    private Map<String, Object> row(String projectId, String companyName) {
        return db.queryForMap("""
                SELECT industry, industry_v2_code, industry_v2_label, sector_group
                FROM app_lm_project_triage_company WHERE project_id = ?::uuid AND company_name = ?
                """, projectId, companyName);
    }

    private void assertFiledAs(String projectId, String companyName, String industry, Integer v2Code,
                               String v2Label, String sectorGroup) {
        assertThat(row(projectId, companyName))
                .containsEntry("industry", industry)
                .containsEntry("industry_v2_code", v2Code)
                .containsEntry("industry_v2_label", v2Label)
                .containsEntry("sector_group", sectorGroup);
    }

    private static String captureUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/triage/capture";
    }

    private String mandate(String firmName) throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        adminToken = login(alok);

        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Forms Client"}"""))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Retail"}
                                """.formatted(clientId)))
                .andReturn()).get("id").asText();
    }
}
