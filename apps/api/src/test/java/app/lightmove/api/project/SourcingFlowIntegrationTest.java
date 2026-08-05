package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The Sourcing read end to end: the companies matching a project's saved Strategy scope (sector, and
 * employee/revenue bands AND'd together), narrowed by an optional company-name filter and ordered
 * either by match tier or by a caller-chosen column, with an empty scope returning an empty page
 * rather than the whole universe.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class SourcingFlowIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;

    @BeforeEach
    void freshUniverse() {
        db.execute("DELETE FROM app_lm_companies");
    }

    @Test
    @DisplayName("a project with no scope sources nothing, without touching the company universe")
    void noScopeSourcesNothing() throws Exception {
        String admin = adminOf("Sourcing Empty Firm");
        String projectId = project(admin);
        company("Alpha Retail", "Retail", "1-10", "<5M");

        mvc.perform(get(sourcingUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies.length()").value(0))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    @DisplayName("sourcing matches the sector scope, narrowed by employee AND revenue bands")
    void sourcingMatchesSectorAndedWithSizeBands() throws Exception {
        String admin = adminOf("Sourcing Match Firm");
        String projectId = project(admin);
        company("Alpha Retail", "Retail", "1-10", "<5M");         // in scope: sector + both bands
        company("Beta Retail", "Retail", "1-10", "5M-25M");       // wrong revenue band
        company("Gamma Retail", "Retail", "201-500", "<5M");      // wrong employee band
        company("Delta Energy", "Oil and Gas", "1-10", "<5M");    // wrong sector

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");
        putCompanySize(admin, projectId, """
                {"employee":["1-10"],"revenue":["<5M"]}""");

        mvc.perform(get(sourcingUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.companies.length()").value(1))
                .andExpect(jsonPath("$.companies[0].name").value("Alpha Retail"));
    }

    @Test
    @DisplayName("pagination slices the matches in stable name order and reports the true total")
    void paginationSlicesInNameOrder() throws Exception {
        String admin = adminOf("Sourcing Page Firm");
        String projectId = project(admin);
        company("Alpha Retail", "Retail", "1-10", "<5M");
        company("Bravo Retail", "Retail", "1-10", "<5M");
        company("Charlie Retail", "Retail", "1-10", "<5M");

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        mvc.perform(get(sourcingUrl(projectId))
                        .param("page", "0").param("size", "2")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.companies.length()").value(2))
                .andExpect(jsonPath("$.companies[0].name").value("Alpha Retail"))
                .andExpect(jsonPath("$.companies[1].name").value("Bravo Retail"));

        mvc.perform(get(sourcingUrl(projectId))
                        .param("page", "1").param("size", "2")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.companies.length()").value(1))
                .andExpect(jsonPath("$.companies[0].name").value("Charlie Retail"));
    }

    @Test
    @DisplayName("a row carries every field the table can show, not only the ones displayed by default")
    void rowCarriesEveryOfferableField() throws Exception {
        String admin = adminOf("Sourcing Wide Row Firm");
        String projectId = project(admin);
        companyWithProfile("Meridian Energy", "Retail");

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        // The visible column set is a client preference, so the response cannot depend on it. A column
        // quietly dropped from the SELECT would otherwise only surface as a blank cell in the UI.
        mvc.perform(get(sourcingUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].website").value("meridian.com"))
                .andExpect(jsonPath("$.companies[0].linkedinUrl").value("linkedin.com/company/meridian"))
                .andExpect(jsonPath("$.companies[0].logo").value("https://cdn.example.com/meridian.png"))
                .andExpect(jsonPath("$.companies[0].slogan").value("Energy for the region"))
                .andExpect(jsonPath("$.companies[0].description").value("An integrated energy group."))
                .andExpect(jsonPath("$.companies[0].founded").value(1998))
                .andExpect(jsonPath("$.companies[0].ownership").value("Privately Held"))
                .andExpect(jsonPath("$.companies[0].ipoStatus").value("Private"))
                .andExpect(jsonPath("$.companies[0].orgType").value("Company"))
                .andExpect(jsonPath("$.companies[0].country").value("AE"))
                .andExpect(jsonPath("$.companies[0].industryTags",
                        containsInAnyOrder("Grocery Retail", "Fuel")))
                .andExpect(jsonPath("$.companies[0].specialties", containsInAnyOrder("Refining")));
    }

    @Test
    @DisplayName("a company with no tags or specialties reports empty lists, never null")
    void missingArrayColumnsComeBackEmpty() throws Exception {
        String admin = adminOf("Sourcing Null Array Firm");
        String projectId = project(admin);
        company("Alpha Retail", "Retail", "1-10", "<5M");

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        // specialties is left NULL by this fixture — the client renders a list and should not have to
        // decide what a null one means.
        mvc.perform(get(sourcingUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].specialties").isEmpty())
                .andExpect(jsonPath("$.companies[0].industryTags").isEmpty());
    }

    @Test
    @DisplayName("each company reports which scope bucket it matched through: direct, adjacent, or inferred")
    void matchTierReflectsWhichBucketMatched() throws Exception {
        String admin = adminOf("Sourcing Tier Firm");
        String projectId = project(admin);
        company("Alpha DirectCo", "Retail", "1-10", "<5M");         // matches the direct sector
        company("Bravo AdjacentCo", "Wholesale", "1-10", "<5M");    // matches the adjacent sector
        companyWithTag("Charlie InferredCo", "Oil and Gas", "Grocery Retail", "1-10", "<5M"); // tag only

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],
                 "adjacent":[{"label":"Wholesale","selected":true}],
                 "inferred":[{"label":"Grocery Retail","selected":true}]}""");

        mvc.perform(get(sourcingUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.companies[0].name").value("Alpha DirectCo"))
                .andExpect(jsonPath("$.companies[0].matchTier").value("DIRECT"))
                .andExpect(jsonPath("$.companies[1].name").value("Bravo AdjacentCo"))
                .andExpect(jsonPath("$.companies[1].matchTier").value("ADJACENT"))
                .andExpect(jsonPath("$.companies[2].name").value("Charlie InferredCo"))
                .andExpect(jsonPath("$.companies[2].matchTier").value("INFERRED"));
    }

    @Test
    @DisplayName("geography narrows results by hq_country or the markets array, either sufficient")
    void geographyNarrowsByCountryOrMarkets() throws Exception {
        String admin = adminOf("Sourcing Geography Firm");
        String projectId = project(admin);
        companyWithGeography("Alpha Retail", "Retail", "AE", new String[0]); // HQ match
        companyWithGeography("Beta Retail", "Retail", "SA", new String[] {"AE"}); // markets-array match
        companyWithGeography("Gamma Retail", "Retail", "KW", new String[0]); // neither

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");
        putGeography(admin, projectId, """
                {"markets":["AE"]}""");

        mvc.perform(get(sourcingUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.companies[*].name",
                        containsInAnyOrder("Alpha Retail", "Beta Retail")));
    }

    @Test
    @DisplayName("a target company never appears in the Sourcing list — targets live in the universe")
    void targetCompanyIsExcludedFromSourcing() throws Exception {
        String admin = adminOf("Sourcing Target Firm");
        String projectId = project(admin);
        companyWithKey("target-co", "Retail", "1-10", "<5M");

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");
        putTargets(admin, projectId, """
                {"companies":[{"source":"test","sourceId":"target-co"}]}""");

        mvc.perform(get(sourcingUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.companies").isEmpty());
    }

    @Test
    @DisplayName("an off-limits company is excluded even though it matches the scope")
    void offLimitsCompanyExcludedDespiteMatching() throws Exception {
        String admin = adminOf("Sourcing OffLimits Firm");
        String projectId = project(admin);
        companyWithKey("off-limits-co", "Retail", "1-10", "<5M");
        company("Kept Retail", "Retail", "1-10", "<5M");

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");
        putOffLimits(admin, projectId, """
                {"companies":[{"source":"test","sourceId":"off-limits-co"}]}""");

        mvc.perform(get(sourcingUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.companies[0].name").value("Kept Retail"));
    }

    @Test
    @DisplayName("a target that also matches the scope is still excluded; only normal matches remain")
    void targetCompanyExcludedEvenWhenItMatchesScope() throws Exception {
        String admin = adminOf("Sourcing Target Priority Firm");
        String projectId = project(admin);
        companyWithKey("both-co", "Retail", "1-10", "<5M");
        company("Kept Retail", "Retail", "1-10", "<5M");

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");
        putTargets(admin, projectId, """
                {"companies":[{"source":"test","sourceId":"both-co"}]}""");

        mvc.perform(get(sourcingUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.companies[0].name").value("Kept Retail"));
    }

    @Test
    @DisplayName("results order by revenue descending, nulls last, breaking ties by name")
    void resultsOrderByRevenueDescending() throws Exception {
        String admin = adminOf("Sourcing Revenue Order Firm");
        String projectId = project(admin);
        companyWithRevenue("Low Retail", "Retail", 1_000_000L);
        companyWithRevenue("High Retail", "Retail", 9_000_000L);
        companyWithNullRevenue("NoRevenue Retail", "Retail", 5);
        companyWithRevenue("Mid Retail", "Retail", 5_000_000L);

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        mvc.perform(get(sourcingUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].name").value("High Retail"))
                .andExpect(jsonPath("$.companies[1].name").value("Mid Retail"))
                .andExpect(jsonPath("$.companies[2].name").value("Low Retail"))
                .andExpect(jsonPath("$.companies[3].name").value("NoRevenue Retail"));
    }

    @Test
    @DisplayName("a tag-only scope (no sectors selected) returns its inferred matches without error")
    void tagOnlyScopeReturnsInferredMatches() throws Exception {
        String admin = adminOf("Sourcing Tag Only Firm");
        String projectId = project(admin);
        companyWithTag("Grocery One", "Oil and Gas", "Grocery Retail", "1-10", "<5M");

        putSectors(admin, projectId, """
                {"direct":[],"adjacent":[],"inferred":[{"label":"Grocery Retail","selected":true}]}""");

        // No sector WHENs in the tier CASE — the match tier is a bare 'INFERRED' literal, not invalid SQL.
        mvc.perform(get(sourcingUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.companies[0].name").value("Grocery One"))
                .andExpect(jsonPath("$.companies[0].matchTier").value("INFERRED"));
    }

    @Test
    @DisplayName("results sort by tier (direct, then adjacent, then inferred) before revenue within a tier")
    void sortsByTierThenRevenueWithinTier() throws Exception {
        String admin = adminOf("Sourcing Tier Order Firm");
        String projectId = project(admin);
        companyWithRevenue("Aaa DirectHigh", "Retail", 8_000_000L);       // DIRECT, higher revenue
        companyWithRevenue("Alpha DirectLow", "Retail", 1_000_000L);      // DIRECT, lower revenue
        companyWithRevenue("Bravo AdjacentCo", "Wholesale", 5_000_000L);  // ADJACENT
        companyWithTagAndRevenue("Zeta InferredCo", "Oil and Gas", "Grocery Retail", 9_000_000_000L); // INFERRED, richest

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],
                 "adjacent":[{"label":"Wholesale","selected":true}],
                 "inferred":[{"label":"Grocery Retail","selected":true}]}""");

        // The 9-billion Inferred company outweighs every Direct on revenue, yet still sorts last: tier
        // leads, revenue only orders within a tier.
        mvc.perform(get(sourcingUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(4))
                .andExpect(jsonPath("$.companies[0].name").value("Aaa DirectHigh"))
                .andExpect(jsonPath("$.companies[0].matchTier").value("DIRECT"))
                .andExpect(jsonPath("$.companies[1].name").value("Alpha DirectLow"))
                .andExpect(jsonPath("$.companies[1].matchTier").value("DIRECT"))
                .andExpect(jsonPath("$.companies[2].name").value("Bravo AdjacentCo"))
                .andExpect(jsonPath("$.companies[2].matchTier").value("ADJACENT"))
                .andExpect(jsonPath("$.companies[3].name").value("Zeta InferredCo"))
                .andExpect(jsonPath("$.companies[3].matchTier").value("INFERRED"));
    }

    @Test
    @DisplayName("a column sort orders by that column alone, in the asked-for direction")
    void columnSortOrdersByThatColumn() throws Exception {
        String admin = adminOf("Sourcing Name Sort Firm");
        String projectId = project(admin);
        // Revenues are set so the default order (revenue desc) is Bravo, Charlie, Alpha — otherwise the
        // ascending assertion below would pass just as well against a sort parameter that is ignored.
        companyWithRevenue("Bravo Retail", "Retail", 9_000_000L);
        companyWithRevenue("Alpha Retail", "Retail", 1_000_000L);
        companyWithRevenue("Charlie Retail", "Retail", 5_000_000L);

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        mvc.perform(get(sourcingUrl(projectId))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].name").value("Bravo Retail"));

        mvc.perform(get(sourcingUrl(projectId))
                        .param("sort", "name").param("direction", "asc")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].name").value("Alpha Retail"))
                .andExpect(jsonPath("$.companies[1].name").value("Bravo Retail"))
                .andExpect(jsonPath("$.companies[2].name").value("Charlie Retail"));

        mvc.perform(get(sourcingUrl(projectId))
                        .param("sort", "name").param("direction", "desc")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].name").value("Charlie Retail"))
                .andExpect(jsonPath("$.companies[1].name").value("Bravo Retail"))
                .andExpect(jsonPath("$.companies[2].name").value("Alpha Retail"));
    }

    @Test
    @DisplayName("a revenue sort outranks the match tier — the richest company leads whatever bucket it came from")
    void revenueSortOverridesMatchTier() throws Exception {
        String admin = adminOf("Sourcing Sort Over Tier Firm");
        String projectId = project(admin);
        companyWithRevenue("Alpha DirectCo", "Retail", 1_000_000L);
        companyWithTagAndRevenue("Zeta InferredCo", "Oil and Gas", "Grocery Retail", 9_000_000_000L);

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],
                 "adjacent":[],
                 "inferred":[{"label":"Grocery Retail","selected":true}]}""");

        // Without a sort the direct match leads (sortsByTierThenRevenueWithinTier); asking for revenue
        // means revenue, or "sort by revenue" would be a lie about what the column does.
        mvc.perform(get(sourcingUrl(projectId))
                        .param("sort", "revenue").param("direction", "desc")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].name").value("Zeta InferredCo"))
                .andExpect(jsonPath("$.companies[0].matchTier").value("INFERRED"))
                .andExpect(jsonPath("$.companies[1].name").value("Alpha DirectCo"));
    }

    @Test
    @DisplayName("an employees sort orders by headcount, not by the range string the column displays")
    void employeeSortOrdersByCountNotRangeString() throws Exception {
        String admin = adminOf("Sourcing Employee Sort Firm");
        String projectId = project(admin);
        companyWithScale("Big Retail", "Retail", 3_000, "1001-5000");
        companyWithScale("Small Retail", "Retail", 120, "51-200");

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        // Sorted as text, "1001-5000" precedes "51-200" and Big Retail would lead an ascending sort.
        mvc.perform(get(sourcingUrl(projectId))
                        .param("sort", "employees").param("direction", "asc")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].name").value("Small Retail"))
                .andExpect(jsonPath("$.companies[1].name").value("Big Retail"));
    }

    @Test
    @DisplayName("a company with no figure sorts last in both directions, never leading the list")
    void missingFiguresSortLastEitherWay() throws Exception {
        String admin = adminOf("Sourcing Null Sort Firm");
        String projectId = project(admin);
        companyWithRevenue("Known Retail", "Retail", 5_000_000L);
        companyWithNullRevenue("Unknown Retail", "Retail", 5);

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        mvc.perform(get(sourcingUrl(projectId))
                        .param("sort", "revenue").param("direction", "asc")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].name").value("Known Retail"))
                .andExpect(jsonPath("$.companies[1].name").value("Unknown Retail"));

        mvc.perform(get(sourcingUrl(projectId))
                        .param("sort", "revenue").param("direction", "desc")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].name").value("Known Retail"))
                .andExpect(jsonPath("$.companies[1].name").value("Unknown Retail"));
    }

    @Test
    @DisplayName("a name filter narrows both the page and the total, matching case-insensitively anywhere in the name")
    void nameFilterNarrowsPageAndTotal() throws Exception {
        String admin = adminOf("Sourcing Name Filter Firm");
        String projectId = project(admin);
        company("Meridian Energy", "Retail", "1-10", "<5M");
        company("Northern MERIDIAN Holdings", "Retail", "1-10", "<5M");
        company("Alpha Retail", "Retail", "1-10", "<5M");

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        mvc.perform(get(sourcingUrl(projectId))
                        .param("q", "meridian")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                // The count is taken under the same filter — a total of 3 over 2 listed rows would be a lie.
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.companies[*].name",
                        containsInAnyOrder("Meridian Energy", "Northern MERIDIAN Holdings")));
    }

    @Test
    @DisplayName("a name filter treats LIKE wildcards as literal characters")
    void nameFilterTreatsWildcardsLiterally() throws Exception {
        String admin = adminOf("Sourcing Wildcard Firm");
        String projectId = project(admin);
        company("Alpha%Retail", "Retail", "1-10", "<5M");
        company("AlphaXRetail", "Retail", "1-10", "<5M");

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        // Unescaped, the % would match any run of characters and pull AlphaXRetail in with it.
        mvc.perform(get(sourcingUrl(projectId))
                        .param("q", "alpha%r")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.companies[0].name").value("Alpha%Retail"));
    }

    @Test
    @DisplayName("with no column sort, a name filter puts prefix matches above mid-name ones")
    void nameFilterRanksPrefixMatchesFirst() throws Exception {
        String admin = adminOf("Sourcing Prefix Rank Firm");
        String projectId = project(admin);
        companyWithRevenue("Northern Meridian Holdings", "Retail", 9_000_000_000L);
        companyWithRevenue("Meridian Energy", "Retail", 1_000_000L);

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        // Someone typing "meri" means the company called Meridian, not the far larger company that
        // happens to contain the letters.
        mvc.perform(get(sourcingUrl(projectId))
                        .param("q", "meri")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].name").value("Meridian Energy"))
                .andExpect(jsonPath("$.companies[1].name").value("Northern Meridian Holdings"));
    }

    @Test
    @DisplayName("paging a sorted list covers every match exactly once")
    void pagingASortedListCoversEveryMatchOnce() throws Exception {
        String admin = adminOf("Sourcing Sorted Paging Firm");
        String projectId = project(admin);
        companyWithScale("Alpha Retail", "Retail", 100, "51-200");
        companyWithScale("Bravo Retail", "Retail", 100, "51-200");
        companyWithScale("Charlie Retail", "Retail", 100, "51-200");

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        MvcResult first = mvc.perform(get(sourcingUrl(projectId))
                        .param("sort", "employees").param("direction", "asc")
                        .param("page", "0").param("size", "2")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult second = mvc.perform(get(sourcingUrl(projectId))
                        .param("sort", "employees").param("direction", "asc")
                        .param("page", "1").param("size", "2")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();

        List<String> paged = new ArrayList<>(namesOf(first));
        paged.addAll(namesOf(second));
        assertThat(paged).containsExactlyInAnyOrder("Alpha Retail", "Bravo Retail", "Charlie Retail");
    }

    @Test
    @DisplayName("a location sort orders by city, unknown locations last")
    void locationSortOrdersByCity() throws Exception {
        String admin = adminOf("Sourcing Location Sort Firm");
        String projectId = project(admin);
        companyInCity("Riyadh Retail", "Retail", "Riyadh", "SA");
        companyInCity("Abu Dhabi Retail", "Retail", "Abu Dhabi", "AE");
        companyInCity("Placeless Retail", "Retail", null, null);

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        mvc.perform(get(sourcingUrl(projectId))
                        .param("sort", "location").param("direction", "asc")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].name").value("Abu Dhabi Retail"))
                .andExpect(jsonPath("$.companies[1].name").value("Riyadh Retail"))
                .andExpect(jsonPath("$.companies[2].name").value("Placeless Retail"));
    }

    @Test
    @DisplayName("a sector sort orders by the sector column, unknown sectors last")
    void sectorSortOrdersBySectorUnknownLast() throws Exception {
        String admin = adminOf("Sourcing Sector Sort Firm");
        String projectId = project(admin);
        company("Zulu Wholesale", "Wholesale", "1-10", "<5M");
        company("Alpha Retail", "Retail", "1-10", "<5M");
        companyWithTagOnlyAndNoSector("Sectorless Co", "Grocery Retail");

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],
                 "adjacent":[{"label":"Wholesale","selected":true}],
                 "inferred":[{"label":"Grocery Retail","selected":true}]}""");

        mvc.perform(get(sourcingUrl(projectId))
                        .param("sort", "sector").param("direction", "asc")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].name").value("Alpha Retail"))
                .andExpect(jsonPath("$.companies[1].name").value("Zulu Wholesale"))
                .andExpect(jsonPath("$.companies[2].name").value("Sectorless Co"));
    }

    @Test
    @DisplayName("the picker's own columns sort too: founded and country, unknowns last")
    void foundedAndCountrySortsOrderTheirColumns() throws Exception {
        String admin = adminOf("Sourcing Origin Sort Firm");
        String projectId = project(admin);
        companyWithOrigin("Young Retail", "Retail", 2015, "SA");
        companyWithOrigin("Old Retail", "Retail", 1962, "AE");
        companyWithOrigin("Undated Retail", "Retail", null, null);

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        mvc.perform(get(sourcingUrl(projectId))
                        .param("sort", "founded").param("direction", "asc")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].name").value("Old Retail"))
                .andExpect(jsonPath("$.companies[1].name").value("Young Retail"))
                .andExpect(jsonPath("$.companies[2].name").value("Undated Retail"));

        mvc.perform(get(sourcingUrl(projectId))
                        .param("sort", "country").param("direction", "asc")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].name").value("Old Retail"))
                .andExpect(jsonPath("$.companies[1].name").value("Young Retail"))
                .andExpect(jsonPath("$.companies[2].name").value("Undated Retail"));
    }

    @Test
    @DisplayName("sorting by tier orders by match quality: direct, then adjacent, then inferred")
    void tierSortOrdersByMatchQuality() throws Exception {
        String admin = adminOf("Sourcing Tier Sort Firm");
        String projectId = project(admin);
        companyWithTag("Tagged Co", "Logistics", "Grocery Retail", "1-10", "<5M");
        company("Direct Co", "Retail", "1-10", "<5M");
        company("Adjacent Co", "Wholesale", "1-10", "<5M");

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],
                 "adjacent":[{"label":"Wholesale","selected":true}],
                 "inferred":[{"label":"Grocery Retail","selected":true}]}""");

        mvc.perform(get(sourcingUrl(projectId))
                        .param("sort", "tier").param("direction", "asc")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].matchTier").value("DIRECT"))
                .andExpect(jsonPath("$.companies[1].matchTier").value("ADJACENT"))
                .andExpect(jsonPath("$.companies[2].matchTier").value("INFERRED"));

        // Descending is the weakest matches first — the way to review what the AI inferred.
        mvc.perform(get(sourcingUrl(projectId))
                        .param("sort", "tier").param("direction", "desc")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].matchTier").value("INFERRED"))
                .andExpect(jsonPath("$.companies[2].matchTier").value("DIRECT"));
    }

    @Test
    @DisplayName("a zero figure sorts with the unknowns, not at the head of an ascending sort")
    void zeroFiguresSortWithTheUnknowns() throws Exception {
        String admin = adminOf("Sourcing Zero Figure Firm");
        String projectId = project(admin);
        companyWithScale("Real Retail", "Retail", 120, "51-200");
        companyWithScale("Unfigured Retail", "Retail", 0, "51-200");

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        // A zero headcount on a row whose range is known is a data gap, same as a null — leading the
        // list with it is exactly what "sort by employees" was trying to avoid.
        mvc.perform(get(sourcingUrl(projectId))
                        .param("sort", "employees").param("direction", "asc")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].name").value("Real Retail"))
                .andExpect(jsonPath("$.companies[1].name").value("Unfigured Retail"));
    }

    @Test
    @DisplayName("an explicit column sort suppresses the filter's prefix ranking")
    void columnSortSuppressesPrefixRanking() throws Exception {
        String admin = adminOf("Sourcing Sort With Filter Firm");
        String projectId = project(admin);
        company("Meridian Energy", "Retail", "1-10", "<5M");
        company("Alpha Meridian Holdings", "Retail", "1-10", "<5M");

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        // Unsorted, "meri" would put Meridian Energy first (nameFilterRanksPrefixMatchesFirst). Asked
        // for a name sort, the prefix rank steps aside — a chosen column is the whole ordering.
        mvc.perform(get(sourcingUrl(projectId))
                        .param("q", "meridian").param("sort", "name").param("direction", "asc")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.companies[0].name").value("Alpha Meridian Holdings"))
                .andExpect(jsonPath("$.companies[1].name").value("Meridian Energy"));
    }

    @Test
    @DisplayName("a direction with no sort field leaves the default tier order alone")
    void directionWithoutSortFieldChangesNothing() throws Exception {
        String admin = adminOf("Sourcing Bare Direction Firm");
        String projectId = project(admin);
        companyWithRevenue("Low Retail", "Retail", 1_000_000L);
        companyWithRevenue("High Retail", "Retail", 9_000_000L);

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        mvc.perform(get(sourcingUrl(projectId))
                        .param("direction", "asc")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].name").value("High Retail"))
                .andExpect(jsonPath("$.companies[1].name").value("Low Retail"));
    }

    @Test
    @DisplayName("a query longer than the cap is rejected, not silently truncated or run")
    void overlongQueryIsRejected() throws Exception {
        String admin = adminOf("Sourcing Long Query Firm");
        String projectId = project(admin);

        mvc.perform(get(sourcingUrl(projectId))
                        .param("q", "x".repeat(101))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("an unknown sort field or direction is rejected rather than silently ignored")
    void unknownSortTokensAreRejected() throws Exception {
        String admin = adminOf("Sourcing Bad Sort Firm");
        String projectId = project(admin);

        mvc.perform(get(sourcingUrl(projectId))
                        .param("sort", "revenue_usd; DROP TABLE app_lm_companies")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(get(sourcingUrl(projectId))
                        .param("sort", "name").param("direction", "sideways")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("another workspace's project is masked, even to a verified user")
    void crossTenantMasked() throws Exception {
        String admin = adminOf("Sourcing Masked Firm");
        String projectId = project(admin);
        String outsider = verifiedUser("Out Sider", "out@other-" + domain);

        MvcResult masked = mvc.perform(get(sourcingUrl(projectId))
                        .header("Authorization", "Bearer " + outsider))
                .andReturn();
        assertThat(masked.getResponse().getStatus()).isEqualTo(404);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String sourcingUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/sourcing";
    }

    private void putSectors(String token, String projectId, String bodyJson) throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/sectors")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk());
    }

    private void putCompanySize(String token, String projectId, String bodyJson) throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/company-size")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk());
    }

    private void putGeography(String token, String projectId, String bodyJson) throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/geography")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk());
    }

    private void putTargets(String token, String projectId, String bodyJson) throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/targets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk());
    }

    private void putOffLimits(String token, String projectId, String bodyJson) throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/off-limits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk());
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
                                {"customName":"Sourcing Client"}"""))
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

    /** The id column is GENERATED ALWAYS, so it is left out; source_id just has to be unique.
     *  {@code employeeRange}/{@code revenueRange} are the wire-format band strings filtering matches
     *  on — this deliberately does not set the numeric employee_count/revenue_usd columns, mirroring
     *  the real warehouse data where those can independently be zero or missing. */
    private void company(String name, String sector, String employeeRange, String revenueRange) {
        companyWithTags(name, sector, new String[0], employeeRange, revenueRange);
    }

    private void companyWithTag(String name, String sector, String tag, String employeeRange, String revenueRange) {
        companyWithTags(name, sector, new String[] {tag}, employeeRange, revenueRange);
    }

    private void companyWithTags(String name, String sector, String[] tags, String employeeRange,
                                  String revenueRange) {
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_lm_companies
                        (source, source_id, name, primary_industry, industry_tags, employee_range, revenue_range)
                    VALUES ('test', gen_random_uuid()::text, ?, ?, ?, ?, ?)""");
            ps.setString(1, name);
            ps.setString(2, sector);
            ps.setArray(3, connection.createArrayOf("text", tags));
            ps.setString(4, employeeRange);
            ps.setString(5, revenueRange);
            return ps;
        });
    }

    /** For the revenue-ordering test only, which applies no company-size scope — sets the numeric
     *  revenue_usd figure the {@code ORDER BY} sorts on, independent of any band. */
    private void companyWithRevenue(String name, String sector, long revenueUsd) {
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_lm_companies
                        (source, source_id, name, primary_industry, industry_tags, revenue_usd)
                    VALUES ('test', gen_random_uuid()::text, ?, ?, ?, ?)""");
            ps.setString(1, name);
            ps.setString(2, sector);
            ps.setArray(3, connection.createArrayOf("text", new String[0]));
            ps.setLong(4, revenueUsd);
            return ps;
        });
    }

    /** A tag-matching company that also carries a numeric revenue_usd — for the tier-vs-revenue
     *  ordering test, where an inferred (tag-only) match must sort below a direct match despite a
     *  larger revenue. */
    private void companyWithTagAndRevenue(String name, String sector, String tag, long revenueUsd) {
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_lm_companies
                        (source, source_id, name, primary_industry, industry_tags, revenue_usd)
                    VALUES ('test', gen_random_uuid()::text, ?, ?, ?, ?)""");
            ps.setString(1, name);
            ps.setString(2, sector);
            ps.setArray(3, connection.createArrayOf("text", new String[] {tag}));
            ps.setLong(4, revenueUsd);
            return ps;
        });
    }

    private void companyWithGeography(String name, String sector, String hqCountry, String[] markets) {
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_lm_companies
                        (source, source_id, name, primary_industry, industry_tags, hq_country, markets)
                    VALUES ('test', gen_random_uuid()::text, ?, ?, ?, ?, ?)""");
            ps.setString(1, name);
            ps.setString(2, sector);
            ps.setArray(3, connection.createArrayOf("text", new String[0]));
            ps.setString(4, hqCountry);
            ps.setArray(5, connection.createArrayOf("text", markets));
            return ps;
        });
    }

    /** A company with a fixed, caller-known source_id so a PUT to targets/off-limits can reference it. */
    private void companyWithKey(String sourceId, String sector, String employeeRange, String revenueRange) {
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_lm_companies
                        (source, source_id, name, primary_industry, industry_tags, employee_range, revenue_range)
                    VALUES ('test', ?, ?, ?, ?, ?, ?)""");
            ps.setString(1, sourceId);
            ps.setString(2, sourceId);
            ps.setString(3, sector);
            ps.setArray(4, connection.createArrayOf("text", new String[0]));
            ps.setString(5, employeeRange);
            ps.setString(6, revenueRange);
            return ps;
        });
    }

    /** A company carrying both the numeric headcount and the range string it displays as — the two
     *  disagree on sort order, which is the point of the employees-sort test. */
    private void companyWithScale(String name, String sector, int employeeCount, String employeeRange) {
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_lm_companies
                        (source, source_id, name, primary_industry, industry_tags, employee_count,
                         employee_range)
                    VALUES ('test', gen_random_uuid()::text, ?, ?, ?, ?, ?)""");
            ps.setString(1, name);
            ps.setString(2, sector);
            ps.setArray(3, connection.createArrayOf("text", new String[0]));
            ps.setInt(4, employeeCount);
            ps.setString(5, employeeRange);
            return ps;
        });
    }

    /** A fully populated row — every column the Sourcing table can offer, for the wide-row test. */
    private void companyWithProfile(String name, String sector) {
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_lm_companies
                        (source, source_id, name, primary_industry, industry_tags, specialties, website,
                         linkedin_url, logo, slogan, description, founded, ownership, ipo_status,
                         org_type, hq_country, hq_city)
                    VALUES ('test', gen_random_uuid()::text, ?, ?, ?, ?, 'meridian.com',
                            'linkedin.com/company/meridian', 'https://cdn.example.com/meridian.png',
                            'Energy for the region', 'An integrated energy group.', 1998,
                            'Privately Held', 'Private', 'Company', 'AE', 'Dubai')""");
            ps.setString(1, name);
            ps.setString(2, sector);
            ps.setArray(3, connection.createArrayOf("text", new String[] {"Grocery Retail", "Fuel"}));
            ps.setArray(4, connection.createArrayOf("text", new String[] {"Refining"}));
            return ps;
        });
    }

    /** A company placed in a city, for the location sort. */
    private void companyInCity(String name, String sector, String hqCity, String hqCountry) {
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_lm_companies
                        (source, source_id, name, primary_industry, industry_tags, hq_city, hq_country)
                    VALUES ('test', gen_random_uuid()::text, ?, ?, ?, ?, ?)""");
            ps.setString(1, name);
            ps.setString(2, sector);
            ps.setArray(3, connection.createArrayOf("text", new String[0]));
            ps.setString(4, hqCity);
            ps.setString(5, hqCountry);
            return ps;
        });
    }

    /** A company with a founding year and a country — the two columns the picker adds a sort for. */
    private void companyWithOrigin(String name, String sector, Integer founded, String hqCountry) {
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_lm_companies
                        (source, source_id, name, primary_industry, industry_tags, founded, hq_country)
                    VALUES ('test', gen_random_uuid()::text, ?, ?, ?, ?, ?)""");
            ps.setString(1, name);
            ps.setString(2, sector);
            ps.setArray(3, connection.createArrayOf("text", new String[0]));
            ps.setObject(4, founded);
            ps.setString(5, hqCountry);
            return ps;
        });
    }

    /** A tag-only match with no primary_industry at all — the sector-sort's "unknown" row. */
    private void companyWithTagOnlyAndNoSector(String name, String tag) {
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_lm_companies (source, source_id, name, industry_tags)
                    VALUES ('test', gen_random_uuid()::text, ?, ?)""");
            ps.setString(1, name);
            ps.setArray(2, connection.createArrayOf("text", new String[] {tag}));
            return ps;
        });
    }

    /** The company names of one response, in the order they were returned. */
    private List<String> namesOf(MvcResult result) throws Exception {
        List<String> names = new ArrayList<>();
        body(result).get("companies").forEach(company -> names.add(company.get("name").asText()));
        return names;
    }

    private void companyWithNullRevenue(String name, String sector, int employeeCount) {
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_lm_companies
                        (source, source_id, name, primary_industry, industry_tags, employee_count)
                    VALUES ('test', gen_random_uuid()::text, ?, ?, ?, ?)""");
            ps.setString(1, name);
            ps.setString(2, sector);
            ps.setArray(3, connection.createArrayOf("text", new String[0]));
            ps.setInt(4, employeeCount);
            return ps;
        });
    }
}
