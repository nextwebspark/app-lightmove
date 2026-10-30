package app.lightmove.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.security.token.Tokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A workspace's two-factor requirement reaches every credential that reads it, not only the browser session: an AI
 * app's connection and a personal API key read as their person does, so they stop where that person is held.
 */
@IntegrationTest
class McpTwoFactorPolicyIntegrationTest extends McpFlowSupport {

    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("a session held at enrolment cannot connect an AI app to its workspace")
    void consentIsRefusedWhileEnrolmentIsOwed() throws Exception {
        String admin = adminOf(domain);
        requireMfa(workspaceOf(admin));

        MvcResult refused = mvc.perform(request(registerClient(), Tokens.generate(), "projects:read")
                .with("workspace_id", workspaceOf(admin)).asPost(admin)).andReturn();

        assertThat(queryParam(body(refused).get("redirectUri").asText(), "error")).isEqualTo("access_denied");
    }

    @Test
    @DisplayName("an AI app connected before the requirement stops answering until its person sets up a factor")
    void anEarlierConnectionStops() throws Exception {
        String admin = adminOf(domain);
        String token = connect(registerClient(), admin, "projects:read").get("access_token").asText();
        assertThat(statusOf(call(token, LIST_TOOLS))).isEqualTo(200);

        requireMfa(workspaceOf(admin));

        assertThat(statusOf(call(token, LIST_TOOLS))).isEqualTo(401);
    }

    @Test
    @DisplayName("a personal API key stops with its owner, while a workspace key, which has no person, reads on")
    void aPersonalKeyStopsWithItsOwner() throws Exception {
        String admin = adminOf(domain);
        String personal = keyOf(admin, "projects:read");
        String service = serviceKeyOf(admin, "projects:read");

        requireMfa(workspaceOf(admin));

        assertThat(statusOf(get("/api/v1/public/me").header("Authorization", "Bearer " + personal))).isEqualTo(401);
        assertThat(statusOf(get("/api/v1/public/me").header("Authorization", "Bearer " + service))).isEqualTo(200);
    }

    private void requireMfa(String workspaceId) {
        jdbc.update("UPDATE app_lm_workspace SET require_mfa = true WHERE id = ?::uuid", workspaceId);
    }
}
