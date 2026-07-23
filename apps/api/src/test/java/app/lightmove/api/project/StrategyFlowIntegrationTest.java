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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The strategy's sector scope end to end: an empty scope is seeded on first read, the snapshot PUT
 * splits the three kinds and preserves order and selection, a second PUT fully replaces, and the
 * guards (duplicate labels, over-long labels, cross-tenant projects) reject.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class StrategyFlowIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("a fresh project has an empty sector scope, seeded on first read")
    void firstReadSeedsEmptyScope() throws Exception {
        String admin = adminOf("Strategy Seed Firm");
        String projectId = project(admin);

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direct.length()").value(0))
                .andExpect(jsonPath("$.adjacent.length()").value(0))
                .andExpect(jsonPath("$.inferred.length()").value(0));
    }

    @Test
    @DisplayName("the snapshot PUT round-trips all three groups, preserving order and selection")
    void snapshotRoundTrips() throws Exception {
        String admin = adminOf("Strategy Snapshot Firm");
        String projectId = project(admin);

        mvc.perform(put(sectorsUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direct":[{"label":"Retail","selected":true},
                                           {"label":"Hospitality","selected":true}],
                                 "adjacent":[{"label":"Wholesale","selected":true},
                                             {"label":"Consumer Services","selected":false}],
                                 "inferred":[{"label":"Grocery Retail","selected":false}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direct[0].label").value("Retail"))
                .andExpect(jsonPath("$.direct[1].label").value("Hospitality"))
                .andExpect(jsonPath("$.adjacent[1].label").value("Consumer Services"))
                .andExpect(jsonPath("$.adjacent[1].selected").value(false))
                .andExpect(jsonPath("$.inferred[0].selected").value(false));

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.direct.length()").value(2))
                .andExpect(jsonPath("$.direct[0].label").value("Retail"))
                .andExpect(jsonPath("$.adjacent.length()").value(2))
                .andExpect(jsonPath("$.inferred[0].label").value("Grocery Retail"));
    }

    @Test
    @DisplayName("a second PUT replaces the whole scope — removed chips are gone")
    void secondPutReplaces() throws Exception {
        String admin = adminOf("Strategy Replace Firm");
        String projectId = project(admin);

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");
        putSectors(admin, projectId, """
                {"direct":[{"label":"Oil and Gas","selected":true}],"adjacent":[],"inferred":[]}""");

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.direct.length()").value(1))
                .andExpect(jsonPath("$.direct[0].label").value("Oil and Gas"));
    }

    @Test
    @DisplayName("a duplicate label within a group is rejected")
    void duplicateWithinGroupRejected() throws Exception {
        String admin = adminOf("Strategy Dup Firm");
        String projectId = project(admin);

        MvcResult result = mvc.perform(put(sectorsUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direct":[{"label":"Retail","selected":true},
                                           {"label":"retail","selected":true}],
                                 "adjacent":[],"inferred":[]}"""))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("an over-long label is rejected by validation")
    void overLongLabelRejected() throws Exception {
        String admin = adminOf("Strategy Long Firm");
        String projectId = project(admin);

        String longLabel = "x".repeat(161);
        mvc.perform(put(sectorsUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direct":[{"label":"%s","selected":true}],"adjacent":[],"inferred":[]}"""
                                .formatted(longLabel)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a fresh project has an empty company-size scope")
    void firstReadSeedsEmptyCompanySize() throws Exception {
        String admin = adminOf("Company Size Seed Firm");
        String projectId = project(admin);

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.length()").value(0))
                .andExpect(jsonPath("$.revenue.length()").value(0));
    }

    @Test
    @DisplayName("the company-size PUT round-trips the selected bands per axis, ordered by the catalog")
    void companySizeRoundTrips() throws Exception {
        String admin = adminOf("Company Size Snapshot Firm");
        String projectId = project(admin);

        // Sent out of catalog order; the response comes back ordered by the enum declaration.
        mvc.perform(put(companySizeUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employee":["51-200","1-10"],"revenue":["5M-25M"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee[0]").value("1-10"))
                .andExpect(jsonPath("$.employee[1]").value("51-200"))
                .andExpect(jsonPath("$.revenue[0]").value("5M-25M"));

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.employee.length()").value(2))
                .andExpect(jsonPath("$.revenue.length()").value(1))
                // The sector scope is untouched by a company-size write.
                .andExpect(jsonPath("$.direct.length()").value(0));
    }

    @Test
    @DisplayName("a second company-size PUT replaces the whole scope")
    void companySizeSecondPutReplaces() throws Exception {
        String admin = adminOf("Company Size Replace Firm");
        String projectId = project(admin);

        putCompanySize(admin, projectId, """
                {"employee":["1-10"],"revenue":["<5M"]}""");
        putCompanySize(admin, projectId, """
                {"employee":["10000+"],"revenue":[]}""");

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.employee.length()").value(1))
                .andExpect(jsonPath("$.employee[0]").value("10000+"))
                .andExpect(jsonPath("$.revenue.length()").value(0));
    }

    @Test
    @DisplayName("an unknown band value is rejected")
    void unknownBandRejected() throws Exception {
        String admin = adminOf("Company Size Unknown Firm");
        String projectId = project(admin);

        MvcResult result = mvc.perform(put(companySizeUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employee":["7-figures"],"revenue":[]}"""))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a duplicate band within an axis is rejected")
    void duplicateBandRejected() throws Exception {
        String admin = adminOf("Company Size Dup Firm");
        String projectId = project(admin);

        MvcResult result = mvc.perform(put(companySizeUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employee":["51-200","51-200"],"revenue":[]}"""))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a fresh project has empty geography and ownership scopes")
    void firstReadSeedsEmptyGeographyAndOwnership() throws Exception {
        String admin = adminOf("Geo Own Seed Firm");
        String projectId = project(admin);

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markets.length()").value(0))
                .andExpect(jsonPath("$.structures.length()").value(0));
    }

    @Test
    @DisplayName("the geography PUT round-trips the selected markets, ordered by the catalog")
    void geographyRoundTrips() throws Exception {
        String admin = adminOf("Geography Snapshot Firm");
        String projectId = project(admin);
        // Seed a sibling section first, so the untouched-by-a-geography-write assertions below actually
        // prove isolation rather than reading a still-empty scope.
        putOwnership(admin, projectId, """
                {"structures":["Privately Held"]}""");

        // Sent out of catalog order; the response comes back ordered by the enum declaration.
        mvc.perform(put(geographyUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"markets":["QA","AE","SA"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markets[0]").value("AE"))
                .andExpect(jsonPath("$.markets[1]").value("SA"))
                .andExpect(jsonPath("$.markets[2]").value("QA"));

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.markets.length()").value(3))
                // The seeded ownership scope survives a geography write.
                .andExpect(jsonPath("$.structures.length()").value(1))
                .andExpect(jsonPath("$.structures[0]").value("Privately Held"));
    }

    @Test
    @DisplayName("the ownership PUT round-trips the selected structures, ordered by the catalog")
    void ownershipRoundTrips() throws Exception {
        String admin = adminOf("Ownership Snapshot Firm");
        String projectId = project(admin);
        // Seed a sibling section first — same isolation proof as the geography round-trip, mirrored.
        putGeography(admin, projectId, """
                {"markets":["AE"]}""");

        mvc.perform(put(ownershipUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"structures":["Public Company","Privately Held"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.structures[0]").value("Privately Held"))
                .andExpect(jsonPath("$.structures[1]").value("Public Company"));

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.structures.length()").value(2))
                // The seeded geography scope survives an ownership write.
                .andExpect(jsonPath("$.markets.length()").value(1))
                .andExpect(jsonPath("$.markets[0]").value("AE"));
    }

    @Test
    @DisplayName("a second geography PUT replaces the whole scope")
    void geographySecondPutReplaces() throws Exception {
        String admin = adminOf("Geography Replace Firm");
        String projectId = project(admin);

        putGeography(admin, projectId, """
                {"markets":["AE","SA"]}""");
        putGeography(admin, projectId, """
                {"markets":["OM"]}""");

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.markets.length()").value(1))
                .andExpect(jsonPath("$.markets[0]").value("OM"));
    }

    @Test
    @DisplayName("a second ownership PUT replaces the whole scope")
    void ownershipSecondPutReplaces() throws Exception {
        String admin = adminOf("Ownership Replace Firm");
        String projectId = project(admin);

        putOwnership(admin, projectId, """
                {"structures":["Privately Held","Partnership"]}""");
        putOwnership(admin, projectId, """
                {"structures":["Public Company"]}""");

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.structures.length()").value(1))
                .andExpect(jsonPath("$.structures[0]").value("Public Company"));
    }

    @Test
    @DisplayName("an unknown market value is rejected — display names are not wire values")
    void unknownMarketRejected() throws Exception {
        String admin = adminOf("Geography Unknown Firm");
        String projectId = project(admin);

        MvcResult result = mvc.perform(put(geographyUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"markets":["Saudi Arabia"]}"""))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("an unknown ownership structure is rejected")
    void unknownStructureRejected() throws Exception {
        String admin = adminOf("Ownership Unknown Firm");
        String projectId = project(admin);

        MvcResult result = mvc.perform(put(ownershipUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"structures":["CO_OPERATIVE"]}"""))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a duplicate market is rejected")
    void duplicateMarketRejected() throws Exception {
        String admin = adminOf("Geography Dup Firm");
        String projectId = project(admin);

        MvcResult result = mvc.perform(put(geographyUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"markets":["AE","AE"]}"""))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a duplicate ownership structure is rejected")
    void duplicateStructureRejected() throws Exception {
        String admin = adminOf("Ownership Dup Firm");
        String projectId = project(admin);

        MvcResult result = mvc.perform(put(ownershipUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"structures":["Privately Held","Privately Held"]}"""))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("another workspace's strategy does not exist, even to a verified user")
    void crossTenantMasked() throws Exception {
        String admin = adminOf("Strategy Masked Firm");
        String projectId = project(admin);
        String outsider = verifiedUser("Out Sider", "out@other-" + domain);

        MvcResult masked = mvc.perform(get(strategyUrl(projectId))
                        .header("Authorization", "Bearer " + outsider))
                .andReturn();
        assertThat(masked.getResponse().getStatus()).isEqualTo(404);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String strategyUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/strategy";
    }

    private static String sectorsUrl(String projectId) {
        return strategyUrl(projectId) + "/sectors";
    }

    private static String companySizeUrl(String projectId) {
        return strategyUrl(projectId) + "/company-size";
    }

    private static String geographyUrl(String projectId) {
        return strategyUrl(projectId) + "/geography";
    }

    private static String ownershipUrl(String projectId) {
        return strategyUrl(projectId) + "/ownership";
    }

    private void putSectors(String token, String projectId, String bodyJson) throws Exception {
        mvc.perform(put(sectorsUrl(projectId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk());
    }

    private void putCompanySize(String token, String projectId, String bodyJson) throws Exception {
        mvc.perform(put(companySizeUrl(projectId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk());
    }

    private void putGeography(String token, String projectId, String bodyJson) throws Exception {
        mvc.perform(put(geographyUrl(projectId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk());
    }

    private void putOwnership(String token, String projectId, String bodyJson) throws Exception {
        mvc.perform(put(ownershipUrl(projectId))
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
