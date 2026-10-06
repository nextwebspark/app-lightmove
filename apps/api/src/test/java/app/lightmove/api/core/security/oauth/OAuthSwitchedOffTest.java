package app.lightmove.api.core.security.oauth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** With {@code lightmove.mcp.enabled=false} the authorization server does not exist: every path of it is a 404. */
@IntegrationTest
@TestPropertySource(properties = "lightmove.mcp.enabled=false")
class OAuthSwitchedOffTest extends FlowTestSupport {

    @Test
    @DisplayName("metadata, authorize, token and the consent reads all answer 404")
    void everythingIsNotFound() throws Exception {
        mvc.perform(get("/.well-known/oauth-authorization-server")).andExpect(status().isNotFound());
        mvc.perform(get("/.well-known/oauth-protected-resource")).andExpect(status().isNotFound());
        mvc.perform(get("/.well-known/oauth-authorization-server/tenant")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/oauth/authorize").param("client_id", "anyone")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/oauth/token").param("grant_type", "refresh_token"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/oauth/consent-context").param("client_id", "anyone"))
                .andExpect(status().isNotFound());
    }
}
