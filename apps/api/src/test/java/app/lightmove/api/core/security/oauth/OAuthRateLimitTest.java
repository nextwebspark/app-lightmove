package app.lightmove.api.core.security.oauth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.security.token.Tokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** The authorize and token budgets. Its own class because the suite runs with rate limiting off. */
@IntegrationTest
@TestPropertySource(properties = {
        "lightmove.auth.rate-limit.enabled=true",
        "lightmove.mcp.authorize-per-minute-per-ip=2",
        "lightmove.mcp.token-per-minute-per-client=2"
})
class OAuthRateLimitTest extends OAuthFlowSupport {

    @Test
    @DisplayName("a client's token requests are refused once its budget is spent, refused attempts counted")
    void tokenBudget() throws Exception {
        String clientId = registerClient();

        exchangeGuess(clientId).andExpect(status().isBadRequest());
        exchangeGuess(clientId).andExpect(status().isBadRequest());
        exchangeGuess(clientId).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("authorize requests from one address are refused once its budget is spent")
    void authorizeBudget() throws Exception {
        String clientId = registerClient();

        mvc.perform(request(clientId, Tokens.generate(), "projects:read").asGet()).andExpect(status().isFound());
        mvc.perform(request(clientId, Tokens.generate(), "projects:read").asGet()).andExpect(status().isFound());
        mvc.perform(request(clientId, Tokens.generate(), "projects:read").asGet())
                .andExpect(status().isTooManyRequests());
    }

    private ResultActions exchangeGuess(String clientId) throws Exception {
        return mvc.perform(post(TOKEN).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", Tokens.generate())
                .param("redirect_uri", REDIRECT)
                .param("client_id", clientId)
                .param("code_verifier", Tokens.generate())
                .param("resource", identity.resourceUrl()));
    }
}
