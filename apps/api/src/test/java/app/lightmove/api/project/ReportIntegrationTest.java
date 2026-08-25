package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * The mandate report: the saved Strategy filter measured against the company universe, gated on the
 * same WORK_VIEW seat as the rest of a project's content — so an attached client representative reads
 * it, and a member with no seat does not.
 *
 * <p>The report and the Strategy screen now measure the <b>same table</b>. That is what removed two of
 * the three caveats this test used to assert: the off-limits bar could not be applied when the two
 * read different universes, and a selected sector could be missing from the report's source entirely.
 * Both are gone, and the tests that pinned them went with them. The remaining caveat is about the
 * data rather than the plumbing — Apollo publishes a revenue figure on a minority of rows.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class ReportIntegrationTest extends FlowTestSupport {

    private static final String RETAIL_FILTER = """
            {"filter":{"industries":["retail"],"keywords":[],"marketSegments":[],"countries":[],
                       "employeeBands":[],"revenueBands":[]}}""";

    @Autowired JdbcTemplate db;

    private ApolloUniverse universe;

    @BeforeEach
    void freshUniverse() {
        universe = new ApolloUniverse(db);
        universe.reset();
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
    @DisplayName("a mandate with no filter reports the whole universe, matching what Strategy shows")
    void anUnscopedMandateReportsTheWholeUniverse() throws Exception {
        String admin = adminOf("Report Unscoped Firm");
        String projectId = projectOf(admin, "Head of Retail");
        universe.company("a1", "One").industry("retail").country("Qatar").employees(10).insert();
        universe.company("a2", "Two").industry("oil & energy").country("Qatar").employees(10).insert();

        // This is a reversal. The criteria model refused to answer without a sector, so an unscoped
        // mandate reported zero; the search screen that replaced it opens on everything, and a report
        // that disagreed with the screen beside it would be the confusing one.
        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.universeCount").value(2))
                .andExpect(jsonPath("$.sectorsInScope").value(0))
                .andExpect(jsonPath("$.marketsInScope").value(0));
    }

    @Test
    @DisplayName("the report measures the filter the strategy saved, broken down by sector and place")
    void theReportMeasuresTheSavedFilter() throws Exception {
        String admin = adminOf("Report Scope Firm");
        String projectId = projectOf(admin, "Head of Retail");
        universe.company("a1", "Spinneys").industry("retail").country("United Arab Emirates")
                .city("Dubai").employees(10).insert();
        universe.company("a2", "Lulu").industry("retail").country("United Arab Emirates")
                .city("Abu Dhabi").employees(10).insert();
        universe.company("a3", "Carrefour Qatar").industry("retail").country("Qatar")
                .city("Doha").employees(10).insert();
        universe.company("a4", "ACWA Power").industry("oil & energy").country("Saudi Arabia")
                .city("Riyadh").employees(10).insert();
        putFilter(admin, projectId, RETAIL_FILTER);

        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.universeCount").value(3))
                .andExpect(jsonPath("$.sectorsInScope").value(1))
                .andExpect(jsonPath("$.sectors[0].label").value("retail"))
                .andExpect(jsonPath("$.sectors[0].count").value(3))
                .andExpect(jsonPath("$.countries[0].label").value("United Arab Emirates"))
                .andExpect(jsonPath("$.countries[0].count").value(2))
                .andExpect(jsonPath("$.cities.length()").value(3));
    }

    @Test
    @DisplayName("the mandate band is absent until the brief states one")
    void mandateBandAbsentUntilStated() throws Exception {
        String admin = adminOf("Report Band Firm");
        String projectId = projectOf(admin, "Head of Retail");

        // Null, not a band of zero, which would read as a stated figure.
        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.mandateBand").doesNotExist());

        mvc.perform(put("/api/v1/projects/" + projectId + "/position")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mandateReason":"SUCCESSION","narrative":"A hands-on CFO.",
                                 "location":"Abu Dhabi, UAE","employmentType":"FULL_TIME_PERMANENT",
                                 "salaryMin":500000,"salaryMax":750000,"currency":"AED",
                                 "confidential":false}"""))
                .andExpect(status().isOk());

        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.mandateBand.min").value(500000))
                .andExpect(jsonPath("$.mandateBand.max").value(750000))
                .andExpect(jsonPath("$.mandateBand.currency").value("AED"));
    }

    @Test
    @DisplayName("the off-limits bar is applied now that both sides share one universe")
    void offLimitsIsApplied() throws Exception {
        String admin = adminOf("Report Off Limits Firm");
        String projectId = projectOf(admin, "Head of Retail");
        universe.company("a1", "Spinneys").industry("retail").country("Qatar").employees(10).insert();
        universe.company("a2", "Barred").industry("retail").country("Qatar").employees(10).insert();
        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/off-limits")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apolloAccountIds":["a2"]}"""))
                .andExpect(status().isOk());

        // The old report could only say the bar was unenforceable — its source had no key for it.
        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.universeCount").value(1))
                .andExpect(jsonPath("$.offLimitsCompanies").value(1));
    }

    @Test
    @DisplayName("a size band matches the raw headcount, and a revenue band flags the excluded unknowns")
    void sizeBandsAndTheRevenueCaveat() throws Exception {
        String admin = adminOf("Report Size Firm");
        String projectId = projectOf(admin, "Head of Retail");
        universe.company("a1", "Small").industry("retail").country("Qatar").employees(120)
                .revenue(null).insert();
        universe.company("a2", "Large").industry("retail").country("Qatar").employees(3_000)
                .revenue(2_000_000_000L).insert();

        putFilter(admin, projectId, """
                {"filter":{"industries":["retail"],"keywords":[],"marketSegments":[],"countries":[],
                           "employeeBands":["2001-5000"],"revenueBands":[]}}""");
        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.universeCount").value(1))
                .andExpect(jsonPath("$.caveats.revenueBandExcludesUnknown").value(false));

        putFilter(admin, projectId, """
                {"filter":{"industries":["retail"],"keywords":[],"marketSegments":[],"countries":[],
                           "employeeBands":[],"revenueBands":["1b-5b"]}}""");
        // A revenue-scoped report measures a tenth of the market, and has to say so.
        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.universeCount").value(1))
                .andExpect(jsonPath("$.caveats.revenueBandExcludesUnknown").value(true));
    }

    @Test
    @DisplayName("a custom revenue range carries the caveat too, not only a band selection")
    void customRevenueRangeCarriesTheCaveat() throws Exception {
        String admin = adminOf("Report Revenue Range Firm");
        String projectId = projectOf(admin, "Head of Retail");
        universe.company("a1", "Silent").industry("retail").country("Qatar").employees(10)
                .revenue(null).insert();
        universe.company("a2", "Stated").industry("retail").country("Qatar").employees(10)
                .revenue(2_000_000_000L).insert();

        putFilter(admin, projectId, """
                {"filter":{"industries":["retail"],"keywords":[],"marketSegments":[],"countries":[],
                           "employeeBands":[],"revenueBands":[],
                           "revenueRange":{"min":1000000000,"max":5000000000}}}""");

        // Bands and the custom range are two modes of one axis: BETWEEN excludes every null just as a
        // band list does, so a range-scoped report measures the same tenth of the market.
        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.universeCount").value(1))
                .andExpect(jsonPath("$.caveats.revenueBandExcludesUnknown").value(true));
    }

    @Test
    @DisplayName("taking the Unknown band with a revenue selection clears the caveat")
    void unknownBandClearsTheRevenueCaveat() throws Exception {
        String admin = adminOf("Report Unknown Band Firm");
        String projectId = projectOf(admin, "Head of Retail");
        universe.company("a1", "Silent").industry("retail").country("Qatar").employees(10)
                .revenue(null).insert();

        putFilter(admin, projectId, """
                {"filter":{"industries":["retail"],"keywords":[],"marketSegments":[],"countries":[],
                           "employeeBands":[],"revenueBands":["1b-5b","unknown"]
                           }}""");

        // Nothing is being hidden if the companies without a figure were deliberately included.
        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.universeCount").value(1))
                .andExpect(jsonPath("$.caveats.revenueBandExcludesUnknown").value(false));
    }

    @Test
    @DisplayName("a country selection matches the name the universe spells out")
    void countrySelectionMatches() throws Exception {
        String admin = adminOf("Report Country Firm");
        String projectId = projectOf(admin, "Head of Retail");
        universe.company("a1", "Dubai Retail").industry("retail").country("United Arab Emirates")
                .employees(10).insert();
        universe.company("a2", "Doha Retail").industry("retail").country("Qatar").employees(10).insert();

        putFilter(admin, projectId, """
                {"filter":{"industries":["retail"],"keywords":[],"marketSegments":[],
                           "countries":["United Arab Emirates"],"employeeBands":[],
                           "revenueBands":[]}}""");

        mvc.perform(get(reportUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.universeCount").value(1))
                .andExpect(jsonPath("$.marketsInScope").value(1));
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private void putFilter(String token, String projectId, String bodyJson) throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/filter")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk());
    }

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
}
