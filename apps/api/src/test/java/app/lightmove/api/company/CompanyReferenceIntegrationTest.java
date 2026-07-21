package app.lightmove.api.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import java.sql.PreparedStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The company-universe reads the Strategy screen depends on. The universe is seeded per test (the
 * table is ETL reference data, empty in a fresh schema), then queried through the real HTTP endpoints
 * as a workspace member — sectors with counts, live tag suggestions, and the match estimate.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class CompanyReferenceIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;

    @BeforeEach
    void freshUniverse() {
        db.execute("DELETE FROM app_lm_companies");
    }

    @Test
    @DisplayName("the sectors endpoint returns distinct sectors with counts, most populous first, nulls excluded")
    void sectorsRankedByCountNullsExcluded() throws Exception {
        String admin = adminOf("Company Sectors Firm");
        company("Retail", "Grocery Retail", "Consumer Services");
        company("Retail", "Grocery Retail");
        company("Oil and Gas", "Energy");
        companyWithoutSector("Consumer Services");

        mvc.perform(get("/api/v1/companies/sectors").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectors.length()").value(2))
                .andExpect(jsonPath("$.sectors[0].name").value("Retail"))
                .andExpect(jsonPath("$.sectors[0].count").value(2))
                .andExpect(jsonPath("$.sectors[1].name").value("Oil and Gas"))
                .andExpect(jsonPath("$.sectors[1].count").value(1));
    }

    @Test
    @DisplayName("suggestions rank co-occurring tags by frequency and exclude ground the sectors already cover")
    void suggestionsRankTagsAndExcludeCoveredGround() throws Exception {
        String admin = adminOf("Company Suggest Firm");
        company("Retail", "Grocery Retail", "Consumer Services");
        company("Retail", "Grocery Retail");
        company("Retail", "Grocery Retail");
        company("Retail", "Consumer Services");
        company("Oil and Gas", "Energy");

        mvc.perform(get("/api/v1/companies/sectors/suggestions")
                        .param("sector", "Retail")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                // "Grocery Retail" (3) ranks above "Consumer Services" (2); "Retail" itself never appears.
                .andExpect(jsonPath("$.inferredTags[0].tag").value("Grocery Retail"))
                .andExpect(jsonPath("$.inferredTags[0].count").value(3))
                .andExpect(jsonPath("$.inferredTags[1].tag").value("Consumer Services"));
    }

    @Test
    @DisplayName("the estimate counts sector matches OR tag matches without double-counting")
    void estimateCountsUnionWithoutDoubleCounting() throws Exception {
        String admin = adminOf("Company Estimate Firm");
        company("Retail", "Grocery Retail");         // matches on sector
        company("Oil and Gas", "Grocery Retail");    // matches on tag
        company("Retail", "Grocery Retail");         // matches on both — counted once
        company("Construction", "Building Materials"); // matches on neither

        MvcResult result = mvc.perform(get("/api/v1/companies/estimate")
                        .param("sector", "Retail")
                        .param("tag", "Grocery Retail")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("an empty scope estimates zero")
    void emptyScopeEstimatesZero() throws Exception {
        String admin = adminOf("Company Empty Firm");
        company("Retail", "Grocery Retail");

        mvc.perform(get("/api/v1/companies/estimate").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("the search matches case-insensitive substrings, ranking prefix matches first")
    void searchMatchesSubstringsPrefixFirst() throws Exception {
        String admin = adminOf("Company Search Firm");
        namedCompany("Acme Retail", 500);
        namedCompany("Retail Kings", 50);
        namedCompany("Big Retail Kings", 5000);
        namedCompany("Oil and Gas Co", 100);

        mvc.perform(get("/api/v1/companies/search")
                        .param("q", "retail")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies.length()").value(3))
                // The prefix match leads even though a substring match is far larger.
                .andExpect(jsonPath("$.companies[0].name").value("Retail Kings"))
                .andExpect(jsonPath("$.companies[1].name").value("Big Retail Kings"))
                .andExpect(jsonPath("$.companies[2].name").value("Acme Retail"));
    }

    @Test
    @DisplayName("the search returns the picker's display fields")
    void searchReturnsDisplayFields() throws Exception {
        String admin = adminOf("Company Search Fields Firm");
        db.update("""
                        INSERT INTO app_lm_companies (source, source_id, name, domain, slogan, logo,
                                                      primary_industry, hq_city, hq_country, employee_count)
                        VALUES ('test', 'acme', 'Acme Retail', 'acme.example', 'Everything store',
                                'https://logo/acme.png', 'Retail', 'Dubai', 'AE', 500)""");

        mvc.perform(get("/api/v1/companies/search")
                        .param("q", "acme")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].source").value("test"))
                .andExpect(jsonPath("$.companies[0].sourceId").value("acme"))
                .andExpect(jsonPath("$.companies[0].domain").value("acme.example"))
                .andExpect(jsonPath("$.companies[0].slogan").value("Everything store"))
                .andExpect(jsonPath("$.companies[0].logo").value("https://logo/acme.png"))
                .andExpect(jsonPath("$.companies[0].primaryIndustry").value("Retail"))
                .andExpect(jsonPath("$.companies[0].hqCity").value("Dubai"))
                .andExpect(jsonPath("$.companies[0].hqCountry").value("AE"))
                .andExpect(jsonPath("$.companies[0].employeeCount").value(500));
    }

    @Test
    @DisplayName("a single character already searches — there is no minimum query length")
    void singleCharacterSearches() throws Exception {
        String admin = adminOf("Company Search Short Firm");
        revenueCompany("Xylem Retail", "Retail", 100L);
        revenueCompany("Acme Retail", "Retail", 500L);

        mvc.perform(get("/api/v1/companies/search")
                        .param("q", "x")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies.length()").value(1))
                .andExpect(jsonPath("$.companies[0].name").value("Xylem Retail"));
    }

    @Test
    @DisplayName("a blank query browses by revenue, most prominent first, nulls last")
    void blankQueryBrowsesByRevenueDescending() throws Exception {
        String admin = adminOf("Company Browse Firm");
        revenueCompany("Mid Retail", "Retail", 100L);
        revenueCompany("Big Retail", "Retail", 500L);
        revenueCompany("Unknown Retail", "Retail", null);

        mvc.perform(get("/api/v1/companies/search").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies.length()").value(3))
                .andExpect(jsonPath("$.companies[0].name").value("Big Retail"))
                .andExpect(jsonPath("$.companies[1].name").value("Mid Retail"))
                .andExpect(jsonPath("$.companies[2].name").value("Unknown Retail"));
    }

    @Test
    @DisplayName("ascending browse lists the smallest known revenues and drops unknown ones")
    void ascendingBrowseExcludesUnknownRevenue() throws Exception {
        String admin = adminOf("Company Browse Asc Firm");
        revenueCompany("Mid Retail", "Retail", 100L);
        revenueCompany("Big Retail", "Retail", 500L);
        revenueCompany("Unknown Retail", "Retail", null);

        mvc.perform(get("/api/v1/companies/search")
                        .param("order", "revenue_asc")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies.length()").value(2))
                .andExpect(jsonPath("$.companies[0].name").value("Mid Retail"))
                .andExpect(jsonPath("$.companies[1].name").value("Big Retail"));
    }

    @Test
    @DisplayName("the sector filter narrows a browse but is ignored once the user types")
    void sectorFilterNarrowsBrowseOnly() throws Exception {
        String admin = adminOf("Company Browse Sector Firm");
        revenueCompany("Big Retail", "Retail", 500L);
        revenueCompany("Big Energy", "Oil and Gas", 900L);

        mvc.perform(get("/api/v1/companies/search")
                        .param("sector", "Retail")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies.length()").value(1))
                .andExpect(jsonPath("$.companies[0].name").value("Big Retail"));

        mvc.perform(get("/api/v1/companies/search")
                        .param("q", "big").param("sector", "Retail")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies.length()").value(2));
    }

    @Test
    @DisplayName("an unknown order token is rejected")
    void unknownOrderRejected() throws Exception {
        String admin = adminOf("Company Order Firm");

        MvcResult result = mvc.perform(get("/api/v1/companies/search")
                        .param("order", "alphabetical")
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("an over-long query is rejected")
    void overLongQueryRejected() throws Exception {
        String admin = adminOf("Company Long Query Firm");

        MvcResult result = mvc.perform(get("/api/v1/companies/search")
                        .param("q", "x".repeat(101))
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("LIKE wildcards in the query match literally, not as patterns")
    void searchEscapesWildcards() throws Exception {
        String admin = adminOf("Company Search Escape Firm");
        namedCompany("100% Retail", 10);
        namedCompany("Acme Retail", 500);

        mvc.perform(get("/api/v1/companies/search")
                        .param("q", "0% ")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies.length()").value(1))
                .andExpect(jsonPath("$.companies[0].name").value("100% Retail"));
    }

    @Test
    @DisplayName("the search limit is respected and clamped")
    void searchLimitClamped() throws Exception {
        String admin = adminOf("Company Search Limit Firm");
        for (int i = 0; i < 30; i++) {
            namedCompany("Retail Chain " + i, i);
        }

        mvc.perform(get("/api/v1/companies/search")
                        .param("q", "retail").param("limit", "2")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.companies.length()").value(2));

        // A limit beyond the cap comes back capped at 25, and none omitted defaults to 10.
        mvc.perform(get("/api/v1/companies/search")
                        .param("q", "retail").param("limit", "500")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.companies.length()").value(25));
        mvc.perform(get("/api/v1/companies/search")
                        .param("q", "retail")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.companies.length()").value(10));
    }

    @Test
    @DisplayName("employee and revenue bands narrow the estimate, AND'd with each other and with the sector scope")
    void sizeBandsNarrowEstimateAndedAcrossAxes() throws Exception {
        String admin = adminOf("Company Size Estimate Firm");
        companyWithSize("Retail", "1-10", "<5M");      // in sector, 1-10 band, <5M — matches both bands
        companyWithSize("Retail", "1-10", "5M-25M");   // in sector, 1-10 band, but wrong revenue band
        companyWithSize("Retail", "201-500", "<5M");   // in sector, right revenue, wrong employee band
        companyWithSize("Oil and Gas", "1-10", "<5M"); // right size, wrong sector

        mvc.perform(get("/api/v1/companies/estimate")
                        .param("sector", "Retail")
                        .param("employeeBand", "1-10")
                        .param("revenueBand", "<5M")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @DisplayName("bands within one axis OR together")
    void bandsWithinAxisOr() throws Exception {
        String admin = adminOf("Company Size Or Firm");
        companyWithSize("Retail", "1-10", null);
        companyWithSize("Retail", "51-200", null);
        companyWithSize("Retail", "201-500", null); // not selected

        mvc.perform(get("/api/v1/companies/estimate")
                        .param("sector", "Retail")
                        .param("employeeBand", "1-10")
                        .param("employeeBand", "51-200")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    @DisplayName("the open-ended top band has no upper bound")
    void openEndedTopBandHasNoUpperBound() throws Exception {
        String admin = adminOf("Company Size Open Firm");
        companyWithSize("Retail", "10000+", null);

        mvc.perform(get("/api/v1/companies/estimate")
                        .param("sector", "Retail")
                        .param("employeeBand", "10000+")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @DisplayName("a company still matches its employee band even when its raw employee_count is 0 or unset")
    void employeeBandMatchesEvenWithMissingRawCount() throws Exception {
        String admin = adminOf("Company Size Zero Count Firm");
        // The exact real-data shape this test pins: employee_range is the warehouse's authoritative
        // band label, but employee_count can independently be 0 or missing for the same row — filtering
        // must match employee_range directly rather than recomputing bounds off employee_count.
        companyWithSizeAndZeroCount("Retail", "1-10");

        mvc.perform(get("/api/v1/companies/estimate")
                        .param("sector", "Retail")
                        .param("employeeBand", "1-10")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @DisplayName("an unknown band value is rejected")
    void unknownBandValueRejected() throws Exception {
        String admin = adminOf("Company Size Bad Band Firm");

        MvcResult result = mvc.perform(get("/api/v1/companies/estimate")
                        .param("sector", "Retail")
                        .param("employeeBand", "not-a-band")
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("a market code narrows the estimate to that HQ country")
    void marketNarrowsEstimateToHqCountry() throws Exception {
        String admin = adminOf("Company Market Firm");
        companyWithGeography("Retail", "AE");
        companyWithGeography("Retail", "SA");

        mvc.perform(get("/api/v1/companies/estimate")
                        .param("sector", "Retail")
                        .param("market", "AE")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @DisplayName("an unknown market code is rejected")
    void unknownMarketRejected() throws Exception {
        String admin = adminOf("Company Bad Market Firm");

        MvcResult result = mvc.perform(get("/api/v1/companies/estimate")
                        .param("sector", "Retail")
                        .param("market", "US")
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("the company universe is not readable without authentication")
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/v1/companies/sectors"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/companies/search").param("q", "acme"))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String adminOf(String workspaceName) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), workspaceName);
        return login("alok@" + domain);
    }

    private void company(String sector, String... tags) {
        insert(sector, tags);
    }

    private void companyWithoutSector(String... tags) {
        insert(null, tags);
    }

    private void namedCompany(String name, int employeeCount) {
        db.update("""
                        INSERT INTO app_lm_companies (source, source_id, name, employee_count)
                        VALUES ('test', gen_random_uuid()::text, ?, ?)""",
                name, employeeCount);
    }

    private void revenueCompany(String name, String sector, Long revenueUsd) {
        db.update("""
                        INSERT INTO app_lm_companies (source, source_id, name, primary_industry, revenue_usd)
                        VALUES ('test', gen_random_uuid()::text, ?, ?, ?)""",
                name, sector, revenueUsd);
    }

    /** {@code employeeRange}/{@code revenueRange} are the wire-format band strings filtering matches on;
     *  either may be {@code null} to leave that axis unset. */
    private void companyWithSize(String sector, String employeeRange, String revenueRange) {
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_lm_companies
                        (source, source_id, name, primary_industry, industry_tags, employee_range, revenue_range)
                    VALUES ('test', gen_random_uuid()::text, 'Test Co', ?, ?, ?, ?)""");
            ps.setString(1, sector);
            ps.setArray(2, connection.createArrayOf("text", new String[0]));
            ps.setString(3, employeeRange);
            ps.setString(4, revenueRange);
            return ps;
        });
    }

    /** A company whose employee_range is set but employee_count is left at its real-data-observed 0 —
     *  the exact shape of the 5,045-row bug this filtering fix corrects. */
    private void companyWithSizeAndZeroCount(String sector, String employeeRange) {
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_lm_companies
                        (source, source_id, name, primary_industry, industry_tags, employee_count, employee_range)
                    VALUES ('test', gen_random_uuid()::text, 'Test Co', ?, ?, 0, ?)""");
            ps.setString(1, sector);
            ps.setArray(2, connection.createArrayOf("text", new String[0]));
            ps.setString(3, employeeRange);
            return ps;
        });
    }

    private void companyWithGeography(String sector, String hqCountry) {
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_lm_companies
                        (source, source_id, name, primary_industry, industry_tags, hq_country)
                    VALUES ('test', gen_random_uuid()::text, 'Test Co', ?, ?, ?)""");
            ps.setString(1, sector);
            ps.setArray(2, connection.createArrayOf("text", new String[0]));
            ps.setString(3, hqCountry);
            return ps;
        });
    }

    /** The id column is GENERATED ALWAYS, so it is left out; source_id just has to be unique. */
    private void insert(String sector, String[] tags) {
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_lm_companies (source, source_id, name, primary_industry, industry_tags)
                    VALUES ('test', gen_random_uuid()::text, 'Test Co', ?, ?)""");
            ps.setString(1, sector);
            ps.setArray(2, connection.createArrayOf("text", tags));
            return ps;
        });
    }
}
