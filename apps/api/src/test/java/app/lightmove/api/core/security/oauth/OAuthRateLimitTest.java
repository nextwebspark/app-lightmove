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

/** The authorization server's budgets. Its own class because the suite runs with rate limiting off. */
@IntegrationTest
@TestPropertySource(properties = {
        "lightmove.auth.rate-limit.enabled=true",
        "lightmove.mcp.authorize-per-minute-per-ip=2",
        "lightmove.mcp.token-per-minute-per-client=2",
        "lightmove.mcp.register-per-hour-per-ip=2",
        "lightmove.mcp.revoke-per-minute-per-ip=2"
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
    @DisplayName("a client's spent budget at one address refuses nobody at another")
    void clientBudgetIsPerAddress() throws Exception {
        String clientId = registerClient();

        exchangeGuess(clientId).andExpect(status().isBadRequest());
        exchangeGuess(clientId).andExpect(status().isBadRequest());
        exchangeGuess(clientId).andExpect(status().isTooManyRequests());

        mvc.perform(post(TOKEN).with(request -> {
                    request.setRemoteAddr("203.0.113.9");
                    return request;
                }).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", Tokens.generate())
                .param("redirect_uri", REDIRECT)
                .param("client_id", clientId)
                .param("code_verifier", Tokens.generate())
                .param("resource", identity.resourceUrl()))
                .andExpect(status().isBadRequest());
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

    @Test
    @DisplayName("dynamic registrations from one address are refused once its hourly budget is spent")
    void registrationBudget() throws Exception {
        String metadata = """
                {"client_name":"Some app","redirect_uris":["https://app.example.com/callback"],
                 "token_endpoint_auth_method":"none"}""";
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(metadata))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(metadata))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"));
    }

    @Test
    @DisplayName("revocations from one address are refused once its budget is spent")
    void revocationBudget() throws Exception {
        String clientId = registerClient();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(REVOKE).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("client_id", clientId).param("token", Tokens.generate())).andExpect(status().isOk());
        }
        mvc.perform(post(REVOKE).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", clientId).param("token", Tokens.generate()))
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
