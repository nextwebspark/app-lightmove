package app.lightmove.api.strategy;

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
import app.lightmove.api.strategy.service.UniverseReloadWatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The Strategy screen end to end: an empty filter is seeded on first read and matches the whole
 * universe, each axis narrows it, the axes combine, and the guards reject.
 *
 * <p>The behaviour worth pinning hardest is the opening state. The criteria model this replaced
 * refused to answer without a sector — an empty scope matched nothing — and the search screen that
 * replaced it must do the opposite, because a filter panel that opens on zero results reads as an
 * empty market rather than as an untouched filter.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class StrategyFlowIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;
    @Autowired UniverseReloadWatch reloadWatch;

    private ApolloUniverse universe;

    @BeforeEach
    void freshUniverse() {
        universe = new ApolloUniverse(db, reloadWatch);
        universe.reset();
    }

    @Test
    @DisplayName("a fresh project has an empty filter, seeded on first read")
    void firstReadSeedsEmptyFilter() throws Exception {
        String admin = adminOf("Strategy Seed Firm");
        String projectId = project(admin);

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filter.industries.length()").value(0))
                .andExpect(jsonPath("$.filter.marketSegments.length()").value(0))
                .andExpect(jsonPath("$.filter.countries.length()").value(0))
                .andExpect(jsonPath("$.filter.employeeBands.length()").value(0))
                .andExpect(jsonPath("$.filter.revenueBands.length()").value(0))
                .andExpect(jsonPath("$.filter.employeeRange").doesNotExist())
                .andExpect(jsonPath("$.filter.revenueRange").doesNotExist())
                .andExpect(jsonPath("$.offLimits.length()").value(0))
                .andExpect(jsonPath("$.searches.length()").value(0));
    }

    @Test
    @DisplayName("an untouched filter lists the whole universe, not nothing")
    void untouchedFilterListsEverything() throws Exception {
        String admin = adminOf("Strategy Everything Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").country("Saudi Arabia")
                .employees(3_000).insert();
        universe.company("a2", "Masdar").industry("renewables & environment").country("United Arab Emirates")
                .employees(900).insert();

        mvc.perform(get(companiesUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.companies.length()").value(2));
    }

    @Test
    @DisplayName("the filter round-trips as one snapshot and a second PUT fully replaces it")
    void filterRoundTripsAndReplaces() throws Exception {
        String admin = adminOf("Strategy Snapshot Firm");
        String projectId = project(admin);

        putFilter(admin, projectId, """
                {"filter":{"industries":["oil & energy","utilities"],"marketSegments":["B2B"],
                           "countries":["Qatar"],"employeeBands":["1001-2000"],
                           "revenueBands":["1b-5b"]}}""")
                .andExpect(jsonPath("$.filter.industries[0]").value("oil & energy"))
                .andExpect(jsonPath("$.filter.industries[1]").value("utilities"))
                .andExpect(jsonPath("$.filter.marketSegments[0]").value("B2B"))
                .andExpect(jsonPath("$.filter.employeeBands[0]").value("1001-2000"));

        putFilter(admin, projectId, """
                {"filter":{"industries":["retail"],"marketSegments":[],"countries":[],
                           "employeeBands":[],"revenueBands":[]}}""");

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.filter.industries.length()").value(1))
                .andExpect(jsonPath("$.filter.industries[0]").value("retail"))
                .andExpect(jsonPath("$.filter.employeeBands.length()").value(0))
                .andExpect(jsonPath("$.filter.employeeRange").doesNotExist())
                .andExpect(jsonPath("$.filter.revenueRange").doesNotExist());
    }

    @Test
    @DisplayName("a custom range survives the round trip through the jsonb column")
    void customRangeSurvivesTheRoundTrip() throws Exception {
        String admin = adminOf("Strategy Custom Range Firm");
        String projectId = project(admin);
        universe.company("a1", "Mid Cap").industry("retail").country("Qatar").employees(700).insert();
        universe.company("a2", "Small Cap").industry("retail").country("Qatar").employees(20).insert();

        putFilter(admin, projectId, """
                {"filter":{"industries":[],"marketSegments":[],"countries":[],
                           "employeeBands":[],"revenueBands":[],
                           "employeeRange":{"min":500,"max":1000}}}""");

        // Jackson read NumericRange.isEmpty() as a bean property, wrote "empty" into the document and
        // then refused to read it back, so a mandate that used Custom Range could never be loaded
        // again — this GET, its results, its report and bulk add all 500ed on the next request.
        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filter.employeeRange.min").value(500))
                .andExpect(jsonPath("$.filter.employeeRange.max").value(1000));

        mvc.perform(get(strategyUrl(projectId) + "/companies").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.companies[0].companyName").value("Mid Cap"));
    }

    @Test
    @DisplayName("each axis narrows the list, and the axes combine")
    void axesNarrowAndCombine() throws Exception {
        String admin = adminOf("Strategy Narrowing Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").country("Saudi Arabia")
                .employees(3_000).revenue(6_000_000_000L).keywords("b2b").insert();
        universe.company("a2", "Yellow Door Energy").industry("oil & energy").country("United Arab Emirates")
                .employees(120).revenue(null).keywords("b2b", "saas").insert();
        universe.company("a3", "Spinneys").industry("retail").country("United Arab Emirates")
                .employees(9_000).revenue(800_000_000L).keywords("b2c").insert();

        assertCount(admin, projectId, """
                {"industries":["oil & energy"],"marketSegments":[],"countries":[],
                 "employeeBands":[],"revenueBands":[]}""", 2);
        assertCount(admin, projectId, """
                {"industries":[],"marketSegments":[],"countries":["United Arab Emirates"],
                 "employeeBands":[],"revenueBands":[]}""", 2);
        assertCount(admin, projectId, """
                {"industries":[],"marketSegments":["SaaS"],"countries":[],
                 "employeeBands":[],"revenueBands":[]}""", 1);
        assertCount(admin, projectId, """
                {"industries":[],"marketSegments":[],"countries":[],
                 "employeeBands":["2001-5000"],"revenueBands":[]}""", 1);
        // Two axes are an AND: UAE alone is two companies, oil & energy alone is two, together one.
        assertCount(admin, projectId, """
                {"industries":["oil & energy"],"marketSegments":[],"countries":["United Arab Emirates"],
                 "employeeBands":[],"revenueBands":[]}""", 1);
    }

    @Test
    @DisplayName("selecting several values on one axis is an OR")
    void valuesWithinAnAxisAreOred() throws Exception {
        String admin = adminOf("Strategy Or Firm");
        String projectId = project(admin);
        universe.company("a1", "One").industry("oil & energy").employees(10).insert();
        universe.company("a2", "Two").industry("retail").employees(10).insert();
        universe.company("a3", "Three").industry("banking").employees(10).insert();

        assertCount(admin, projectId, """
                {"industries":["oil & energy","retail"],"marketSegments":[],"countries":[],
                 "employeeBands":[],"revenueBands":[]}""", 2);
    }

    @Test
    @DisplayName("the Unknown revenue band reaches the companies that publish no figure")
    void unknownRevenueBandReachesTheUnpublished() throws Exception {
        String admin = adminOf("Strategy Unknown Revenue Firm");
        String projectId = project(admin);
        universe.company("a1", "Published").industry("retail").employees(50).revenue(2_000_000_000L).insert();
        universe.company("a2", "Unpublished").industry("retail").employees(50).revenue(null).insert();

        // A numeric band excludes the row with no figure: it cannot be shown to fall in the band.
        assertCount(admin, projectId, """
                {"industries":[],"marketSegments":[],"countries":[],"employeeBands":[],
                 "revenueBands":["1b-5b"]}""", 1);
        // Unknown is the only way to reach it, which is why the band exists at all.
        assertCount(admin, projectId, """
                {"industries":[],"marketSegments":[],"countries":[],"employeeBands":[],
                 "revenueBands":["unknown"]}""", 1);
        assertCount(admin, projectId, """
                {"industries":[],"marketSegments":[],"countries":[],"employeeBands":[],
                 "revenueBands":["1b-5b","unknown"]}""", 2);
    }

    @Test
    @DisplayName("the name query narrows the count as well as the page")
    void nameQueryNarrowsTheCount() throws Exception {
        String admin = adminOf("Strategy Query Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").employees(10).insert();
        universe.company("a2", "Masdar").industry("oil & energy").employees(10).insert();

        // A count taken without the query would advertise two matches over one listed row.
        mvc.perform(get(companiesUrl(projectId)).param("q", "acwa")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.companies[0].companyName").value("ACWA Power"));
    }

    @Test
    @DisplayName("a name query matches a literal percent rather than every company")
    void nameQueryEscapesWildcards() throws Exception {
        String admin = adminOf("Strategy Wildcard Firm");
        String projectId = project(admin);
        universe.company("a1", "100% Energy").industry("oil & energy").employees(10).insert();
        universe.company("a2", "Masdar").industry("oil & energy").employees(10).insert();

        mvc.perform(get(companiesUrl(projectId)).param("q", "%")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.companies[0].companyName").value("100% Energy"));
    }

    @Test
    @DisplayName("the row carries the logo and description the table renders")
    void rowCarriesDisplayFields() throws Exception {
        String admin = adminOf("Strategy Row Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").country("Saudi Arabia")
                .city("Riyadh").employees(3_000).revenue(6_000_000_000L)
                .website("https://www.acwapower.com").logo("https://cdn.example/acwa.png")
                .description("IPP leader, renewables pivot").founded(2004).insert();

        mvc.perform(get(companiesUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].logoUrl").value("https://cdn.example/acwa.png"))
                .andExpect(jsonPath("$.companies[0].shortDescription")
                        .value("IPP leader, renewables pivot"))
                .andExpect(jsonPath("$.companies[0].companyCity").value("Riyadh"))
                .andExpect(jsonPath("$.companies[0].numEmployees").value(3000))
                .andExpect(jsonPath("$.companies[0].foundedYear").value(2004));
    }

    @Test
    @DisplayName("a company with no revenue reports null rather than zero")
    void missingRevenueIsNullNotZero() throws Exception {
        String admin = adminOf("Strategy Null Revenue Firm");
        String projectId = project(admin);
        universe.company("a1", "Unpublished").industry("retail").employees(50).revenue(null).insert();

        // Zero would render as a stated figure of nothing, which is a different claim from "unknown".
        mvc.perform(get(companiesUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.companies[0].annualRevenue").doesNotExist());
    }

    @Test
    @DisplayName("an off-limits company is excluded from the list and from the count")
    void offLimitsExcludedUnconditionally() throws Exception {
        String admin = adminOf("Strategy Off Limits Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").employees(10).insert();
        universe.company("a2", "Masdar").industry("oil & energy").employees(10).insert();
        putOffLimits(admin, projectId, """
                {"apolloAccountIds":["a2"]}""");

        // There is no show-anyway mode. The panel says these are "completely excluded from your
        // active sourcing search results", and the count has to agree with the rows or the screen
        // is advertising a company it will not show.
        mvc.perform(get(companiesUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.companies.length()").value(1))
                .andExpect(jsonPath("$.companies[0].companyName").value("ACWA Power"));
    }

    @Test
    @DisplayName("the off-limits list stores a server-resolved snapshot, not the client's words")
    void offLimitsStoresAResolvedSnapshot() throws Exception {
        String admin = adminOf("Strategy Snapshot List Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").country("Saudi Arabia")
                .city("Riyadh").logo("https://cdn.example/acwa.png").employees(10).insert();

        mvc.perform(put(offLimitsUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apolloAccountIds":["a1"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offLimits[0].companyName").value("ACWA Power"))
                .andExpect(jsonPath("$.offLimits[0].industry").value("oil & energy"))
                .andExpect(jsonPath("$.offLimits[0].companyCity").value("Riyadh"))
                .andExpect(jsonPath("$.offLimits[0].logoUrl").value("https://cdn.example/acwa.png"));
    }

    @Test
    @DisplayName("a stored off-limits entry survives its company leaving the universe")
    void storedOffLimitsSurvivesAVanishedCompany() throws Exception {
        String admin = adminOf("Strategy Vanished Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").employees(10).insert();
        universe.company("a2", "Masdar").industry("oil & energy").employees(10).insert();
        putOffLimits(admin, projectId, """
                {"apolloAccountIds":["a1","a2"]}""");

        db.update("DELETE FROM app_lm_apollo_companies WHERE apollo_account_id = 'a1'");

        // Removing a2 must not fail because a1's company vanished: only new ids are re-resolved.
        mvc.perform(put(offLimitsUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apolloAccountIds":["a1"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offLimits.length()").value(1))
                .andExpect(jsonPath("$.offLimits[0].companyName").value("ACWA Power"));
    }

    @Test
    @DisplayName("an off-limits id the universe does not hold is rejected")
    void unknownOffLimitsIdRejected() throws Exception {
        String admin = adminOf("Strategy Unknown Id Firm");
        String projectId = project(admin);

        MvcResult result = mvc.perform(put(offLimitsUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apolloAccountIds":["nope"]}"""))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("the same company twice on the off-limits list is rejected")
    void duplicateOffLimitsRejected() throws Exception {
        String admin = adminOf("Strategy Duplicate Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").employees(10).insert();

        MvcResult result = mvc.perform(put(offLimitsUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apolloAccountIds":["a1","a1"]}"""))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a repeated chip is de-duplicated rather than rejected")
    void repeatedChipIsDeduplicated() throws Exception {
        String admin = adminOf("Strategy Dedupe Firm");
        String projectId = project(admin);

        // Unlike a duplicate on the off-limits list, this one has an obvious right answer.
        putFilter(admin, projectId, """
                {"filter":{"industries":["retail","retail"],"marketSegments":[],"countries":[],
                           "employeeBands":[],"revenueBands":[]}}""")
                .andExpect(jsonPath("$.filter.industries.length()").value(1));
    }

    @Test
    @DisplayName("an unknown band is rejected, where an unknown industry simply narrows to nothing")
    void unknownBandRejectedUnknownIndustryTolerated() throws Exception {
        String admin = adminOf("Strategy Band Guard Firm");
        String projectId = project(admin);

        // A band names a closed catalog this codebase owns, so an unknown one is a client bug.
        MvcResult rejected = mvc.perform(put(filterUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"filter":{"industries":[],"marketSegments":[],"countries":[],
                                           "employeeBands":["500-600"],"revenueBands":[]
                                           }}"""))
                .andReturn();
        assertThat(rejected.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(rejected)).isEqualTo("VALIDATION_FAILED");

        // An industry comes from the facets response verbatim; one the universe has stopped carrying
        // should narrow to nothing rather than 400 a save the user cannot fix.
        putFilter(admin, projectId, """
                {"filter":{"industries":["a sector that left the universe"],"marketSegments":[],
                           "countries":[],"employeeBands":[],"revenueBands":[]
                           }}""");
        mvc.perform(get(companiesUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    @DisplayName("a missing list on the filter is rejected rather than read as no constraint")
    void missingListRejected() throws Exception {
        String admin = adminOf("Strategy Missing List Firm");
        String projectId = project(admin);

        MvcResult result = mvc.perform(put(filterUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"filter":{"industries":[],"countries":[],"employeeBands":[],
                                           "revenueBands":[]}}"""))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("paging is stable and the total is the count over the filter, not the page")
    void pagingIsStable() throws Exception {
        String admin = adminOf("Strategy Paging Firm");
        String projectId = project(admin);
        for (int index = 0; index < 5; index++) {
            universe.company("a" + index, "Company " + index).industry("retail")
                    .employees(100 - index).insert();
        }

        MvcResult first = mvc.perform(get(companiesUrl(projectId))
                        .param("page", "0").param("size", "2")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.totalCount").value(5))
                .andExpect(jsonPath("$.companies.length()").value(2))
                .andReturn();
        MvcResult second = mvc.perform(get(companiesUrl(projectId))
                        .param("page", "1").param("size", "2")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.companies.length()").value(2))
                .andReturn();

        assertThat(body(first).get("companies").get(0).get("apolloAccountId").asText())
                .isNotEqualTo(body(second).get("companies").get(0).get("apolloAccountId").asText());
    }

    @Test
    @DisplayName("an over-large page and an unknown sort token are rejected")
    void pagingAndSortGuards() throws Exception {
        String admin = adminOf("Strategy Guard Firm");
        String projectId = project(admin);

        MvcResult tooBig = mvc.perform(get(companiesUrl(projectId)).param("size", "5000")
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertThat(tooBig.getResponse().getStatus()).isEqualTo(400);

        MvcResult badSort = mvc.perform(get(companiesUrl(projectId)).param("sort", "name; DROP TABLE")
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertThat(badSort.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(badSort)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("another workspace's project is a 404, not an empty strategy")
    void foreignProjectIsNotFound() throws Exception {
        String owner = adminOf("Strategy Owner Firm");
        String projectId = project(owner);
        createWorkspace(verifiedUser("Other Admin", "other@" + domain), "Strategy Other Firm");
        String stranger = login("other@" + domain);

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
    }

    private void assertCount(String token, String projectId, String filterJson, int expected)
            throws Exception {
        putFilter(token, projectId, "{\"filter\":" + filterJson + "}");
        mvc.perform(get(companiesUrl(projectId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(expected));
    }

    private org.springframework.test.web.servlet.ResultActions putFilter(String token, String projectId,
                                                                          String bodyJson)
            throws Exception {
        return mvc.perform(put(filterUrl(projectId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk());
    }

    private void putOffLimits(String token, String projectId, String bodyJson) throws Exception {
        mvc.perform(put(offLimitsUrl(projectId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk());
    }

    private static String strategyUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/strategy";
    }

    private static String filterUrl(String projectId) {
        return strategyUrl(projectId) + "/filter";
    }

    private static String offLimitsUrl(String projectId) {
        return strategyUrl(projectId) + "/off-limits";
    }

    private static String companiesUrl(String projectId) {
        return strategyUrl(projectId) + "/companies";
    }

    private String adminOf(String workspaceName) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), workspaceName);
        return login("alok@" + domain);
    }

    private String project(String token) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Strategy Client"}"""))
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
