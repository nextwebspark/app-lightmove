package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * The mandate report: the saved Strategy scope measured against the company universe, gated on the
 * same WORK_VIEW seat as the rest of a project's content — so an attached client representative reads
 * it, and a member with no seat does not.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class ReportIntegrationTest extends FlowTestSupport {

    private static final String RETAIL_SCOPE = """
            {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""";

    @Autowired JdbcTemplate db;

    @BeforeEach
    void freshUniverse() {
        db.execute("DELETE FROM app_lm_apollo_companies");
        db.execute("DELETE FROM app_lm_companies");
    }

    @Test
    @DisplayName("a member with no seat on the mandate cannot read its report")
    void unseatedMemberCannotRead() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Report Unseated Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        String projectId = projectOf(admin, "Head of Retail");

        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + login(sara)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a seated researcher reads the report, and so does the client representative on the mandate")
    void everySeatedRoleReadsTheReport() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Report Seats Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");

        String clientId = clientOf(admin, "Aurora Capital");
        String projectId = projectOf(admin, clientId, "Head of Retail");

        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberIdOf(admin, sara))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"RESEARCHER"}"""))
                .andExpect(status().isOk());
        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + login(sara)))
                .andExpect(status().isOk());

        String repEmail = "chair@aurora-capital.example";
        JsonNode representative = body(mvc.perform(post("/api/v1/clients/" + clientId + "/representatives")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ext Rep","position":"Chair","email":"%s"}
                                """.formatted(repEmail)))
                .andExpect(status().isCreated())
                .andReturn());
        String rep = body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"Ext Rep","password":"%s"}
                                """.formatted(email.latestTokenFor(repEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representativeId":"%s"}
                                """.formatted(representative.get("id").asText())))
                .andExpect(status().isOk());

        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("another firm's mandate id is a 404, not a refusal that confirms it exists")
    void aForeignMandateIsNotFound() throws Exception {
        String admin = adminOf("Report Isolation Firm");
        String projectId = projectOf(admin, "Head of Retail");

        String rivalEmail = "boss@rival-" + domain;
        createWorkspace(verifiedUser("Rival Boss", rivalEmail), "Report Rival Firm");

        // 404, not 403 — through the @PreAuthorize guard, a foreign id must confirm nothing.
        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + login(rivalEmail)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("reading the report writes nothing — a client representative's page load is not a write")
    void readingTheReportSeedsNoStrategy() throws Exception {
        String admin = adminOf("Report Read Only Firm");
        String projectId = projectOf(admin, "Head of Retail");

        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        Long strategies = db.queryForObject(
                "SELECT count(*) FROM app_lm_strategy WHERE project_id = ?::uuid", Long.class, projectId);
        assertThat(strategies).isZero();
    }

    @Test
    @DisplayName("a mandate with no scope reports nothing rather than the whole universe")
    void anUnscopedMandateReportsNothing() throws Exception {
        String admin = adminOf("Report Empty Firm");
        String projectId = projectOf(admin, "Head of Retail");
        apolloCompany("Alpha Retail", "retail", "United Arab Emirates", "Dubai");

        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.universeCount").value(0))
                .andExpect(jsonPath("$.sectorsInScope").value(0))
                .andExpect(jsonPath("$.sectors.length()").value(0))
                .andExpect(jsonPath("$.countries.length()").value(0))
                .andExpect(jsonPath("$.relevance.length()").value(0));
    }

    @Test
    @DisplayName("the report measures the scope the strategy saved, broken down by tier, sector and place")
    void theReportMeasuresTheSavedScope() throws Exception {
        String admin = adminOf("Report Scope Firm");
        String projectId = projectOf(admin, "Head of Retail");
        // Apollo lower-cases every industry and spells countries out; the strategy below selects the
        // Title Case labels the Strategy screen stores, so this is the case-fold in action.
        apolloCompany("Alpha Retail", "retail", "United Arab Emirates", "Dubai");
        apolloCompany("Bravo Retail", "retail", "United Arab Emirates", "Dubai");
        apolloCompany("Charlie Grocery", "grocery stores", "Saudi Arabia", "Riyadh");
        apolloCompany("Delta Energy", "oil & energy", "Saudi Arabia", "Riyadh");
        // In scope and counted, but it carries no city — a bar labelled with a blank is not a finding.
        apolloCompany("Echo Retail", "retail", "United Arab Emirates", null);

        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/sectors")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direct":[{"label":"Retail","selected":true}],
                                 "adjacent":[{"label":"Grocery Stores","selected":true}],
                                 "inferred":[]}"""))
                .andExpect(status().isOk());

        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                // Delta Energy is out of scope, so the universe is four, not five.
                .andExpect(jsonPath("$.universeCount").value(4))
                .andExpect(jsonPath("$.sectorsInScope").value(2))
                .andExpect(jsonPath("$.relevance[0].label").value("DIRECT"))
                .andExpect(jsonPath("$.relevance[0].count").value(3))
                .andExpect(jsonPath("$.relevance[1].label").value("ADJACENT"))
                .andExpect(jsonPath("$.relevance[1].count").value(1))
                .andExpect(jsonPath("$.sectors[0].label").value("retail"))
                .andExpect(jsonPath("$.sectors[0].count").value(3))
                .andExpect(jsonPath("$.countries[0].label").value("United Arab Emirates"))
                .andExpect(jsonPath("$.countries[0].count").value(3))
                // Echo Retail counts in the universe and its country, but contributes no city bar.
                .andExpect(jsonPath("$.cities.length()").value(2))
                .andExpect(jsonPath("$.cities[0].label").value("Dubai"))
                .andExpect(jsonPath("$.cities[0].count").value(2));
    }

    @Test
    @DisplayName("a scope of inferred tags alone reports every match as inferred")
    void aTagOnlyScopeIsAllInferred() throws Exception {
        String admin = adminOf("Report Tag Scope Firm");
        String projectId = projectOf(admin, "Head of Retail");
        apolloCompanyWithKeyword("Alpha Logistics", "transportation", "cold chain", "United Arab Emirates");
        apolloCompanyWithKeyword("Bravo Logistics", "warehousing", "cold chain", "United Arab Emirates");
        apolloCompany("Charlie Retail", "retail", "United Arab Emirates", "Dubai");

        // No sector at all: the match-tier CASE collapses to a bare 'INFERRED' literal, which is the
        // only report shape whose generated SQL differs structurally.
        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/sectors")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direct":[],"adjacent":[],
                                 "inferred":[{"label":"Cold Chain","selected":true}]}"""))
                .andExpect(status().isOk());

        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.universeCount").value(2))
                .andExpect(jsonPath("$.sectorsInScope").value(0))
                .andExpect(jsonPath("$.relevance.length()").value(1))
                .andExpect(jsonPath("$.relevance[0].label").value("INFERRED"))
                .andExpect(jsonPath("$.relevance[0].count").value(2));
    }

    @Test
    @DisplayName("the mandate band is absent until the brief states one")
    void theMandateBandFollowsTheBrief() throws Exception {
        String admin = adminOf("Report Band Firm");
        String projectId = projectOf(admin, "Chief Financial Officer");

        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mandateBand").doesNotExist());

        mvc.perform(put("/api/v1/projects/" + projectId + "/position")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mandateReason":"NEW_ROLE","salaryMin":900000,"salaryMax":1300000,
                                 "currency":"USD","benefits":[],"confidential":false}"""))
                .andExpect(status().isOk());

        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mandateBand.min").value(900000))
                .andExpect(jsonPath("$.mandateBand.max").value(1300000))
                .andExpect(jsonPath("$.mandateBand.currency").value("USD"));
    }

    @Test
    @DisplayName("an off-limits company still counts, and the report says the bar could not be applied")
    void offLimitsCannotBeAppliedAndSaysSo() throws Exception {
        String admin = adminOf("Report Lists Firm");
        String projectId = projectOf(admin, "Head of Retail");
        // The off-limits list is picked from the warehouse registry, so the barred company is seeded
        // there; the report measures Apollo, where that (source, source_id) key does not exist.
        String barred = company("Bravo Retail", "Retail", "AE", "Dubai");
        apolloCompany("Alpha Retail", "retail", "United Arab Emirates", "Dubai");
        apolloCompany("Bravo Retail", "retail", "United Arab Emirates", "Dubai");

        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/sectors")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RETAIL_SCOPE))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/off-limits")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companies":[{"source":"test","sourceId":"%s"}]}
                                """.formatted(barred)))
                .andExpect(status().isOk());

        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offLimitsCompanies").value(1))
                .andExpect(jsonPath("$.targetCompanies").value(0))
                // Both Apollo rows still count: the bar cannot reach this source, so the report states
                // that rather than quietly reporting a universe the reader would take as filtered.
                .andExpect(jsonPath("$.universeCount").value(2))
                .andExpect(jsonPath("$.caveats.offLimitsNotApplied").value(1));
    }

    @Test
    @DisplayName("a selected sector this source does not carry is named, not reported as an empty market")
    void aSectorAbsentFromTheSourceIsNamed() throws Exception {
        String admin = adminOf("Report Absent Sector Firm");
        String projectId = projectOf(admin, "Head of Retail");
        apolloCompany("Alpha Retail", "retail", "United Arab Emirates", "Dubai");

        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/sectors")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direct":[{"label":"Retail","selected":true},
                                           {"label":"Nanotechnology","selected":true}],
                                 "adjacent":[],"inferred":[]}"""))
                .andExpect(status().isOk());

        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.universeCount").value(1))
                .andExpect(jsonPath("$.caveats.sectorsNotInSource.length()").value(1))
                .andExpect(jsonPath("$.caveats.sectorsNotInSource[0]").value("Nanotechnology"));
    }

    @Test
    @DisplayName("a size band matches Apollo's raw headcount, and a revenue band excludes the unknowns")
    void sizeBandsResolveToNumericBounds() throws Exception {
        String admin = adminOf("Report Bands Firm");
        String projectId = projectOf(admin, "Head of Retail");
        apolloCompanyWithSize("Small Retail", "retail", "United Arab Emirates", 40, 3_000_000L);
        apolloCompanyWithSize("Mid Retail", "retail", "United Arab Emirates", 120, 3_000_000L);
        // In band on headcount, but Apollo carries no revenue figure for it.
        apolloCompanyWithSize("Unknown Revenue Retail", "retail", "United Arab Emirates", 30, null);

        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/sectors")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RETAIL_SCOPE))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/company-size")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employee":["11-50"],"revenue":[]}"""))
                .andExpect(status().isOk());

        // "11-50" is a range string in the warehouse and a pair of bounds on num_employees here.
        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.universeCount").value(2))
                .andExpect(jsonPath("$.caveats.revenueBandExcludesUnknown").value(false));

        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/company-size")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employee":["11-50"],"revenue":["<5M"]}"""))
                .andExpect(status().isOk());

        // The no-figure row drops out: it cannot be shown to fall in the band, and the report says so.
        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.universeCount").value(1))
                .andExpect(jsonPath("$.caveats.revenueBandExcludesUnknown").value(true));
    }

    @Test
    @DisplayName("an ISO market selection matches the country name Apollo spells out")
    void marketsResolveToApolloCountryNames() throws Exception {
        String admin = adminOf("Report Market Firm");
        String projectId = projectOf(admin, "Head of Retail");
        apolloCompany("Dubai Retail", "retail", "United Arab Emirates", "Dubai");
        apolloCompany("Riyadh Retail", "retail", "Saudi Arabia", "Riyadh");

        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/sectors")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RETAIL_SCOPE))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/geography")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"markets":["AE"]}"""))
                .andExpect(status().isOk());

        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.universeCount").value(1))
                .andExpect(jsonPath("$.marketsInScope").value(1))
                .andExpect(jsonPath("$.countries[0].label").value("United Arab Emirates"));
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private static String reportUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/report";
    }

    private String adminOf(String firmName) throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        return login(alok);
    }

    private String clientOf(String adminToken, String name) throws Exception {
        return body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"%s"}""".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String projectOf(String adminToken, String positionTitle) throws Exception {
        return projectOf(adminToken, clientOf(adminToken, "Report Client"), positionTitle);
    }

    private String projectOf(String adminToken, String clientId, String positionTitle) throws Exception {
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s"}
                                """.formatted(clientId, positionTitle)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    /** One Apollo row. Industries arrive lower-cased and countries spelled out, as Apollo stores them. */
    private void apolloCompany(String name, String industry, String country, String city) {
        apolloRow(name, industry, new String[0], country, city, null, null);
    }

    private void apolloCompanyWithKeyword(String name, String industry, String keyword, String country) {
        apolloRow(name, industry, new String[] {keyword}, country, "Dubai", null, null);
    }

    private void apolloCompanyWithSize(String name, String industry, String country, Integer numEmployees,
                                        Long annualRevenue) {
        apolloRow(name, industry, new String[0], country, "Dubai", numEmployees, annualRevenue);
    }

    private void apolloRow(String name, String industry, String[] keywords, String country, String city,
                            Integer numEmployees, Long annualRevenue) {
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_lm_apollo_companies
                        (apollo_account_id, company_name, industry, keywords, company_country,
                         company_city, num_employees, annual_revenue, row_hash)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""");
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, name);
            ps.setString(3, industry);
            ps.setArray(4, connection.createArrayOf("text", keywords));
            ps.setString(5, country);
            ps.setString(6, city);
            ps.setObject(7, numEmployees);
            ps.setObject(8, annualRevenue);
            ps.setString(9, UUID.randomUUID().toString());
            return ps;
        });
    }

    /**
     * One warehouse row, returning its {@code source_id}. Only the off-limits test needs this: the
     * strategy's company lists are picked from the warehouse registry even though the report measures
     * Apollo, which is exactly why the bar cannot be applied there.
     */
    private String company(String name, String sector, String hqCountry, String hqCity) {
        return companyWithTags(name, sector, new String[0], hqCountry, hqCity);
    }

    private String companyWithTags(String name, String sector, String[] tags, String hqCountry,
                                    String hqCity) {
        String sourceId = UUID.randomUUID().toString();
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_lm_companies
                        (source, source_id, name, primary_industry, industry_tags, hq_country, hq_city)
                    VALUES ('test', ?, ?, ?, ?, ?, ?)""");
            ps.setString(1, sourceId);
            ps.setString(2, name);
            ps.setString(3, sector);
            ps.setArray(4, connection.createArrayOf("text", tags));
            ps.setString(5, hqCountry);
            ps.setString(6, hqCity);
            return ps;
        });
        return sourceId;
    }
}
