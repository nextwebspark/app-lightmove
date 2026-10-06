package app.lightmove.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.security.oauth.OAuthFlowSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** The MCP call budget. Its own class because the suite runs with rate limiting off. */
@IntegrationTest
@TestPropertySource(properties = {
        "lightmove.auth.rate-limit.enabled=true",
        "lightmove.mcp.calls-per-minute-per-credential=2"
})
class McpRateLimitTest extends OAuthFlowSupport {

    @Test
    @DisplayName("a connection's calls are refused once its budget is spent, with Retry-After")
    void credentialBudget() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "MCP Firm");
        String token = connect(registerClient(), login(alok), "projects:read").get("access_token").asText();

        assertThat(listTools(token).getResponse().getStatus()).isEqualTo(200);
        assertThat(listTools(token).getResponse().getStatus()).isEqualTo(200);
        MvcResult refused = listTools(token);
        assertThat(refused.getResponse().getStatus()).isEqualTo(429);
        assertThat(refused.getResponse().getHeader("Retry-After")).isEqualTo("60");
    }

    private MvcResult listTools(String token) throws Exception {
        return mvc.perform(post("/api/v1/mcp").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}")).andReturn();
    }
}
