package app.lightmove.api.core.security.oauth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** With {@code lightmove.mcp.enabled=false} neither the authorization server nor the MCP server exists: all a 404. */
@IntegrationTest
@TestPropertySource(properties = "lightmove.mcp.enabled=false")
class OAuthSwitchedOffTest extends FlowTestSupport {

    @Test
    @DisplayName("metadata, authorize, token, the consent reads, the MCP endpoint and its metadata all answer 404")
    void everythingIsNotFound() throws Exception {
        mvc.perform(get("/.well-known/oauth-authorization-server")).andExpect(status().isNotFound());
        mvc.perform(get("/.well-known/oauth-protected-resource")).andExpect(status().isNotFound());
        mvc.perform(get("/.well-known/oauth-authorization-server/tenant")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/oauth/authorize").param("client_id", "anyone")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/oauth/token").param("grant_type", "refresh_token"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/oauth/consent-context").param("client_id", "anyone"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/.well-known/oauth-protected-resource/api/v1/mcp")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/mcp").contentType("application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                .andExpect(status().isNotFound());
    }
}
