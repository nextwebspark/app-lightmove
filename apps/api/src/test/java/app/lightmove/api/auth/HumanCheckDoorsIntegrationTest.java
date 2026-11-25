package app.lightmove.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every anonymous door that takes a password or sends an email asks for the human check when it is on. A request
 * with no token is refused before anything is read, so no call to Cloudflare is made and none is needed here.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "lightmove.auth.human-check.enabled=true",
        "lightmove.auth.human-check.site-key=test-site-key",
        "lightmove.auth.human-check.secret-key=test-secret-key",
        "lightmove.auth.human-check.verify-url=http://127.0.0.1:9/unreachable"
})
class HumanCheckDoorsIntegrationTest {

    @Autowired MockMvc mvc;

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "/api/v1/auth/login|{\"email\":\"ghost@firm.example\",\"password\":\"secret123\"}",
            "/api/v1/auth/signup|{\"fullName\":\"Ghost\",\"email\":\"ghost@firm.example\",\"password\":\"secret123\",\"termsAccepted\":true}",
            "/api/v1/auth/password/forgot|{\"email\":\"ghost@firm.example\"}",
            "/api/v1/auth/sso/discover|{\"email\":\"ghost@firm.example\"}",
            "/api/v1/onboarding/accept-invitation-signup|{\"token\":\"t\",\"fullName\":\"Ghost\",\"password\":\"secret123\",\"termsAccepted\":true}"
    })
    @DisplayName("a door refuses a request carrying no human-check token")
    void refusesWithoutAToken(String path, String body) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HUMAN_CHECK_FAILED"));
    }

    @Test
    @DisplayName("the SPA is given the site key, and its pages may load Cloudflare's challenge")
    void offersTheSiteKeyAndAllowsTheWidget() throws Exception {
        mvc.perform(get("/api/v1/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.humanCheckSiteKey").value("test-site-key"));

        mvc.perform(get("/index.html"))
                .andExpect(header().string("Content-Security-Policy",
                        Matchers.containsString("script-src 'self' https://challenges.cloudflare.com;")));
    }
}
