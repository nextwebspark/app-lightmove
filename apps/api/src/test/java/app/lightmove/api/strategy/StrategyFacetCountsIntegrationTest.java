package app.lightmove.api.strategy;

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
import org.springframework.test.web.servlet.ResultActions;

/**
 * The sidebar's counts as one selection cuts them.
 *
 * <p>The rule every case here is really about: <b>an axis is counted with everything applied except
 * its own criterion</b>. Picking an industry has to recount the bands and segments under it and leave
 * the other industries countable — apply the industry to its own accordion and every row but the
 * chosen one reads zero, which makes Industry useless the moment it is used. That is the failure this
 * file exists to prevent, and it is invisible to any test that only checks the axis it selected on.
 *
 * <p>Location is the axis that is <b>not</b> counted: its pills carry no number, so a country narrows
 * every axis here and is answered by none of them. That has its own failure mode — a criterion no
 * accordion counts is easy to drop from the query altogether — and its own case below.
 *
 * <p>The universe is seeded per test — it is ETL reference data, empty in a fresh schema — and read
 * through the real HTTP endpoint.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class StrategyFacetCountsIntegrationTest extends FlowTestSupport {

    private static final String NO_FILTER = """
            {"industries":[],"keywords":[],"marketSegments":[],"countries":[],
             "employeeBands":[],"revenueBands":[]}""";

    @Autowired JdbcTemplate db;

    private ApolloUniverse universe;

    @BeforeEach
    void freshUniverse() {
        universe = new ApolloUniverse(db);
        universe.reset();
    }

    /**
     * Three UAE companies and two Saudi ones, spread so every axis has something to say about the
     * others.
     */
    private void seedTwoCountries() {
        universe.company("a1", "Emirates Oil").industry("oil & energy").country("United Arab Emirates")
                .employees(120).revenue(5_000_000L).keywords("b2b", "saas").insert();
        universe.company("a2", "Dubai Fintech").industry("banking").country("United Arab Emirates")
                .employees(40).keywords("fintech", "b2b").insert();
        universe.company("a3", "Abu Dhabi Power").industry("utilities").country("United Arab Emirates")
                .employees(9_000).revenue(20_000_000_000L).keywords("b2b").insert();
        universe.company("a4", "Riyadh Oil").industry("oil & energy").country("Saudi Arabia")
                .employees(300).keywords("b2b").insert();
        universe.company("a5", "Jeddah Retail").industry("retail").country("Saudi Arabia")
                .employees(12).revenue(900_000L).keywords("e-commerce").insert();
    }

    @Test
    @DisplayName("an untouched filter counts the whole universe on every axis")
    void untouchedFilterCountsEverything() throws Exception {
        String admin = adminOf("Counts Whole Firm");
        String projectId = project(admin);
        seedTwoCountries();

        facetCounts(admin, projectId, NO_FILTER)
                .andExpect(jsonPath("$.industries['oil & energy']").value(2))
                .andExpect(jsonPath("$.employeeBands['101-200']").value(1))
                .andExpect(jsonPath("$.revenueBands.unknown").value(2))
                .andExpect(jsonPath("$.marketSegments.B2B").value(4))
                // Location is offered by the facets read and counted by neither.
                .andExpect(jsonPath("$.countries").doesNotExist());
    }

    @Test
    @DisplayName("picking an industry recounts every other axis under it, and leaves Industry countable")
    void industryNarrowsTheOtherAxesOnly() throws Exception {
        String admin = adminOf("Counts Industry Firm");
        String projectId = project(admin);
        seedTwoCountries();

        facetCounts(admin, projectId, """
                {"industries":["oil & energy"],"keywords":[],"marketSegments":[],"countries":[],
                 "employeeBands":[],"revenueBands":[]}""")
                // Narrowed: only the two oil & energy rows are counted.
                .andExpect(jsonPath("$.employeeBands['101-200']").value(1))
                .andExpect(jsonPath("$.employeeBands['201-500']").value(1))
                .andExpect(jsonPath("$.employeeBands['21-50']").doesNotExist())
                .andExpect(jsonPath("$.revenueBands.unknown").value(1))
                .andExpect(jsonPath("$.marketSegments.B2B").value(2))
                // Untouched: Industry is counted without its own criterion, so banking still says how
                // many companies clicking it would reach rather than zero.
                .andExpect(jsonPath("$.industries['oil & energy']").value(2))
                .andExpect(jsonPath("$.industries.banking").value(1))
                .andExpect(jsonPath("$.industries.utilities").value(1))
                .andExpect(jsonPath("$.industries.retail").value(1));
    }

    @Test
    @DisplayName("a country narrows every counted axis, and is answered by none of them")
    void countryNarrowsEveryAxisAndIsAnsweredByNone() throws Exception {
        String admin = adminOf("Counts Country Firm");
        String projectId = project(admin);
        seedTwoCountries();

        // No accordion counts Location, so nothing ever excludes the country criterion — which makes
        // it easy to drop from the query altogether and never notice. Every axis below is the check.
        facetCounts(admin, projectId, """
                {"industries":[],"keywords":[],"marketSegments":[],
                 "countries":["United Arab Emirates"],"employeeBands":[],"revenueBands":[]}""")
                .andExpect(jsonPath("$.industries['oil & energy']").value(1))
                .andExpect(jsonPath("$.industries.utilities").value(1))
                .andExpect(jsonPath("$.industries.retail").doesNotExist())
                .andExpect(jsonPath("$.employeeBands['21-50']").value(1))
                .andExpect(jsonPath("$.employeeBands['11-20']").doesNotExist())
                .andExpect(jsonPath("$.revenueBands.unknown").value(1))
                .andExpect(jsonPath("$.marketSegments.B2B").value(3))
                .andExpect(jsonPath("$.countries").doesNotExist());
    }

    @Test
    @DisplayName("an option the selection empties is absent, which the sidebar reads as zero")
    void anEmptiedOptionIsAbsent() throws Exception {
        String admin = adminOf("Counts Zero Firm");
        String projectId = project(admin);
        seedTwoCountries();

        facetCounts(admin, projectId, """
                {"industries":[],"keywords":[],"marketSegments":[],"countries":["Saudi Arabia"],
                 "employeeBands":[],"revenueBands":[]}""")
                .andExpect(jsonPath("$.industries['oil & energy']").value(1))
                // No Saudi company is in banking or utilities. Absent is the wire form of zero — the
                // vocabulary is the facets read's, so repeating it here would only let the two
                // disagree about which industries exist.
                .andExpect(jsonPath("$.industries.banking").doesNotExist())
                .andExpect(jsonPath("$.industries.utilities").doesNotExist());
    }

    @Test
    @DisplayName("each axis drops only its own criterion, so two selections still narrow each other")
    void twoSelectionsStillNarrowEachOther() throws Exception {
        String admin = adminOf("Counts Pair Firm");
        String projectId = project(admin);
        seedTwoCountries();

        facetCounts(admin, projectId, """
                {"industries":["oil & energy"],"keywords":[],"marketSegments":[],
                 "countries":["United Arab Emirates"],"employeeBands":[],"revenueBands":[]}""")
                // Industry drops the industry but keeps the country: all three UAE rows are counted.
                .andExpect(jsonPath("$.industries['oil & energy']").value(1))
                .andExpect(jsonPath("$.industries.banking").value(1))
                .andExpect(jsonPath("$.industries.utilities").value(1))
                // The bands drop neither, so only the one company in both is left.
                .andExpect(jsonPath("$.employeeBands['101-200']").value(1))
                .andExpect(jsonPath("$.employeeBands['201-500']").doesNotExist());
    }

    @Test
    @DisplayName("a custom range narrows the other axes, and its own axis keeps every band countable")
    void customRangeNarrowsTheOtherAxes() throws Exception {
        String admin = adminOf("Counts Range Firm");
        String projectId = project(admin);
        seedTwoCountries();

        facetCounts(admin, projectId, """
                {"industries":[],"keywords":[],"marketSegments":[],"countries":[],
                 "employeeBands":[],"revenueBands":[],"employeeRange":{"min":100,"max":1000}}""")
                // 120 and 300 employees: one UAE oil company and one Saudi one.
                .andExpect(jsonPath("$.industries['oil & energy']").value(2))
                .andExpect(jsonPath("$.industries.banking").doesNotExist())
                // The headcount axis drops the range that is its own mode, so the bands outside it
                // still say what clicking them would reach.
                .andExpect(jsonPath("$.employeeBands['21-50']").value(1))
                .andExpect(jsonPath("$.employeeBands['5001-10000']").value(1));
    }

    @Test
    @DisplayName("off-limits companies are barred from every axis, the one they were picked on included")
    void offLimitsCompaniesAreBarredEverywhere() throws Exception {
        String admin = adminOf("Counts Barred Firm");
        String projectId = project(admin);
        seedTwoCountries();

        // Barring is a standing decision on the stored strategy, never something the counts request
        // carries — so a barred company has to vanish from a count the request knows nothing about.
        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/off-limits")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apolloAccountIds":["a3"]}"""))
                .andExpect(status().isOk());

        facetCounts(admin, projectId, NO_FILTER)
                .andExpect(jsonPath("$.industries.utilities").doesNotExist())
                .andExpect(jsonPath("$.revenueBands['10b-plus']").doesNotExist())
                .andExpect(jsonPath("$.employeeBands['5001-10000']").doesNotExist())
                .andExpect(jsonPath("$.marketSegments.B2B").value(3));
    }

    @Test
    @DisplayName("an empty universe counts nothing rather than failing")
    void anEmptyUniverseCountsNothing() throws Exception {
        String admin = adminOf("Counts Empty Firm");
        String projectId = project(admin);

        facetCounts(admin, projectId, NO_FILTER)
                .andExpect(jsonPath("$.industries.length()").value(0))
                .andExpect(jsonPath("$.employeeBands.length()").value(0))
                // Segments are tallied in one pass over the rows, so every one comes back — at zero.
                .andExpect(jsonPath("$.marketSegments.B2B").value(0));
    }

    @Test
    @DisplayName("a band slug this codebase does not own is a client bug, and says so")
    void unknownBandSlugIsRejected() throws Exception {
        String admin = adminOf("Counts Band Firm");
        String projectId = project(admin);

        mvc.perform(countsRequest(admin, projectId, """
                        {"industries":[],"keywords":[],"marketSegments":[],"countries":[],
                         "employeeBands":["1-10000000"],"revenueBands":[]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a foreign project is not found, whatever the filter asks for")
    void aForeignProjectIsNotFound() throws Exception {
        String owner = adminOf("Counts Owner Firm");
        String projectId = project(owner);
        String stranger = adminOf("Counts Stranger Firm", "stranger");

        mvc.perform(countsRequest(stranger, projectId, NO_FILTER))
                .andExpect(status().isNotFound());
    }

    private ResultActions facetCounts(String token, String projectId, String filterJson)
            throws Exception {
        return mvc.perform(countsRequest(token, projectId, filterJson)).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.RequestBuilder countsRequest(String token,
                                                                             String projectId,
                                                                             String filterJson) {
        return post("/api/v1/projects/" + projectId + "/strategy/facet-counts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"filter\":" + filterJson + "}");
    }

    private String adminOf(String workspaceName) throws Exception {
        return adminOf(workspaceName, "alok");
    }

    private String adminOf(String workspaceName, String mailbox) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", mailbox + "@" + domain), workspaceName);
        return login(mailbox + "@" + domain);
    }

    private String project(String token) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Counts Client"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Retail"}
                                """.formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }
}
