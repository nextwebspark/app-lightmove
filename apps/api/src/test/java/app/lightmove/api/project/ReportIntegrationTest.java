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
        company("Alpha Retail", "Retail", "AE", "Dubai");

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
        company("Alpha Retail", "Retail", "AE", "Dubai");
        company("Bravo Retail", "Retail", "AE", "Dubai");
        company("Charlie Grocery", "Grocery Stores", "SA", "Riyadh");
        company("Delta Energy", "Oil and Gas", "SA", "Riyadh");
        // In scope and counted, but it carries no city — a bar labelled with a blank is not a finding.
        company("Echo Retail", "Retail", "AE", null);

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
                .andExpect(jsonPath("$.sectors[0].label").value("Retail"))
                .andExpect(jsonPath("$.sectors[0].count").value(3))
                .andExpect(jsonPath("$.countries[0].label").value("AE"))
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
        companyWithTag("Alpha Logistics", "Transportation", "Cold Chain", "AE", "Dubai");
        companyWithTag("Bravo Logistics", "Warehousing", "Cold Chain", "AE", "Dubai");
        company("Charlie Retail", "Retail", "AE", "Dubai");

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
    @DisplayName("the target and off-limits lists are counted, and off-limits companies leave the universe")
    void theCompanyListsAreCounted() throws Exception {
        String admin = adminOf("Report Lists Firm");
        String projectId = projectOf(admin, "Head of Retail");
        company("Alpha Retail", "Retail", "AE", "Dubai");
        String barred = company("Bravo Retail", "Retail", "AE", "Dubai");

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
                .andExpect(jsonPath("$.universeCount").value(1));
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

    /** Seeds one universe row and returns its {@code source_id}, the key a strategy list stores. */
    private String company(String name, String sector, String hqCountry, String hqCity) {
        return companyWithTags(name, sector, new String[0], hqCountry, hqCity);
    }

    private String companyWithTag(String name, String sector, String tag, String hqCountry, String hqCity) {
        return companyWithTags(name, sector, new String[] {tag}, hqCountry, hqCity);
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
