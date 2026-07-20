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

    private void putSectors(String token, String projectId, String bodyJson) throws Exception {
        mvc.perform(put(sectorsUrl(projectId))
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
                                {"name":"Strategy Client"}"""))
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
