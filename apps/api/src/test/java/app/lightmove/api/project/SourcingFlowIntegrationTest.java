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
 * employee/revenue bands AND'd together), paginated by name, with an empty scope returning an empty
 * page rather than the whole universe.
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
                .andExpect(jsonPath("$.appliedFilters.sector").value(true))
                .andExpect(jsonPath("$.appliedFilters.employee").value(true))
                .andExpect(jsonPath("$.appliedFilters.revenue").value(true))
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
    @DisplayName("appliedFilters reports only the categories actually in scope — size left unset stays false")
    void appliedFiltersReflectsOnlyWhatsActuallyScoped() throws Exception {
        String admin = adminOf("Sourcing Applied Filters Firm");
        String projectId = project(admin);
        company("Alpha Retail", "Retail", "1-10", "<5M");

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        mvc.perform(get(sourcingUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedFilters.sector").value(true))
                .andExpect(jsonPath("$.appliedFilters.employee").value(false))
                .andExpect(jsonPath("$.appliedFilters.revenue").value(false));
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
                .andExpect(jsonPath("$.appliedFilters.geography").value(true))
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
