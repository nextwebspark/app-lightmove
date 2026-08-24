package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pairing the browser extension with an account.
 *
 * <p>The extension runs on a {@code chrome-extension://} origin, so it cannot be given the refresh
 * cookie — that cookie is {@code SameSite=Strict}, host-only and path-scoped, and the only way to let
 * another origin present it is to take those attributes off. It gets a refresh token of its own
 * instead, in the response body, and the tests below are about the three properties that makes it
 * safe: it is a <b>separate family</b>, it <b>rotates</b> like any other, and minting one <b>requires
 * an authenticated caller</b> while spending one does not.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class ExtensionPairingIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("a signed-in user pairs the extension and gets a refresh token in the body")
    void pairingReturnsARefreshTokenInTheBody() throws Exception {
        String user = signedInUser("Pairing Firm");

        JsonNode paired = body(pair(user));
        assertThat(paired.get("refreshToken").asText()).isNotBlank();
        assertThat(paired.get("accessToken").asText()).isNotBlank();
        // An access token comes with it so the popup can act at once, rather than immediately spending
        // its brand-new refresh token to get one.
        assertThat(paired.get("expiresIn").asLong()).isPositive();
        assertThat(paired.get("user").get("email").asText()).isEqualTo("alok@" + domain);
    }

    @Test
    @DisplayName("minting a token needs a session; spending one does not")
    void mintingIsAuthenticatedAndSpendingIsNot() throws Exception {
        // Nobody may mint a credential for an account they have not proved they hold.
        mvc.perform(post("/api/v1/auth/extension/tokens"))
                .andExpect(status().isUnauthorized());

        String user = signedInUser("Pairing Gate Firm");
        String refreshToken = body(pair(user)).get("refreshToken").asText();

        // The refresh route carries no cookie — the body token is the whole credential — so it is
        // public and CSRF-exempt. No bearer token, no CSRF token, and it still works.
        mvc.perform(post("/api/v1/auth/extension/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("the extension's token rotates, and replaying a spent one kills its family only")
    void rotationAndReuseDetectionApplyToTheExtensionToo() throws Exception {
        String user = signedInUser("Pairing Rotation Firm");
        String original = body(pair(user)).get("refreshToken").asText();

        String successor = body(refresh(original)).get("refreshToken").asText();
        assertThat(successor).isNotEqualTo(original);

        // Replaying the spent one is what a thief with a copied token does, and it is treated as such.
        mvc.perform(post("/api/v1/auth/extension/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(original)))
                .andExpect(status().isUnauthorized());

        // The whole extension family is gone with it...
        mvc.perform(post("/api/v1/auth/extension/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(successor)))
                .andExpect(status().isUnauthorized());

        // ...but the browser session is untouched. Separate families is the point: a stolen extension
        // token must not sign a consultant out of the app they are working in.
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the extension's session is listed as its own device, so it can be revoked from the web")
    void theExtensionSessionIsVisibleAndRevocable() throws Exception {
        String user = signedInUser("Pairing Sessions Firm");
        pair(user);

        // Named rather than left as a second indistinguishable "Chrome — macOS": the extension's own
        // fetches carry the host browser's User-Agent, so without a label of its own a consultant
        // could not tell which entry to end.
        mvc.perform(get("/api/v1/auth/sessions").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.deviceKind == 'EXTENSION')]").isNotEmpty());
    }

    @Test
    @DisplayName("signing out of the extension ends its session and leaves the browser's alone")
    void extensionLogoutIsIndependent() throws Exception {
        String user = signedInUser("Pairing Logout Firm");
        String refreshToken = body(pair(user)).get("refreshToken").asText();

        mvc.perform(post("/api/v1/auth/extension/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/extension/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk());
    }

    private MvcResult pair(String bearerToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/extension/tokens")
                        .header("Authorization", "Bearer " + bearerToken))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private MvcResult refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/extension/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static String refreshBody(String refreshToken) {
        return """
                {"refreshToken":"%s"}""".formatted(refreshToken);
    }

    /** A verified user with a workspace — the extension pairs to a mandate-holding account or to none. */
    private String signedInUser(String workspaceName) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), workspaceName);
        return login("alok@" + domain);
    }
}
