package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import jakarta.servlet.http.Cookie;
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
        // Nobody may mint a credential for an account they have not proved they hold. Asserted as "no
        // token came back" rather than as a status code: chain 1 sets an accessDeniedHandler but no
        // authenticationEntryPoint, so Spring's default answers an anonymous denial with 403 where
        // chain 3, which installs the bearer entry point, answers 401. Which of the two it is, is
        // incidental to this test — that nothing was minted is not.
        MvcResult anonymous = mvc.perform(post("/api/v1/auth/extension/tokens"))
                .andExpect(status().is4xxClientError())
                .andReturn();
        assertThat(anonymous.getResponse().getContentAsString()).doesNotContain("refreshToken");

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
        String workspaceOwner = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", workspaceOwner), "Pairing Sessions Firm");

        // The refresh cookie as well as the bearer token: /auth/sessions marks which row is *this*
        // session, and it can only know that by matching the cookie presented — so without one it
        // refuses rather than guessing. ActiveSessionsIntegrationTest pins that behaviour.
        MvcResult signIn = signInRaw(workspaceOwner);
        String browserToken = body(signIn).get("accessToken").asText();
        Cookie browserCookie = refreshCookieOf(signIn);

        pair(browserToken);

        // Named rather than left as a second indistinguishable "Chrome — macOS": the extension's own
        // fetches carry the host browser's User-Agent, so without a label of its own a consultant
        // could not tell which entry to end.
        mvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + browserToken)
                        .cookie(browserCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.deviceKind == 'EXTENSION')]").isNotEmpty());
    }

    @Test
    @DisplayName("a browser session's refresh token cannot be redeemed on the extension route")
    void aWebTokenIsRefusedOnTheExtensionRoute() throws Exception {
        String workspaceOwner = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", workspaceOwner), "Pairing Client Fence Firm");
        Cookie browserCookie = refreshCookieOf(signInRaw(workspaceOwner));

        // The web refresh token exists only as an httpOnly SameSite=Strict cookie. Redeeming it here
        // would hand its successor back in a plaintext body — laundering a credential kept out of
        // script's reach into a bearer token, and relabelling the browser session as an extension.
        mvc.perform(post("/api/v1/auth/extension/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(browserCookie.getValue())))
                .andExpect(status().isUnauthorized());

        // And it is still a perfectly good cookie for the route it belongs to.
        mvc.perform(post("/api/v1/auth/refresh").cookie(browserCookie).with(csrf()))
                .andExpect(status().isOk());
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

    @Test
    @DisplayName("an extension token is equally refused on the browser's own refresh route")
    void anExtensionTokenIsRefusedOnTheWebRoute() throws Exception {
        String workspaceOwner = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", workspaceOwner), "Pairing Reverse Fence Firm");
        String browserToken = login(workspaceOwner);
        String extensionRefresh = body(pair(browserToken)).get("refreshToken").asText();

        // The other half of the fence, and it has to hold or the fence is a suggestion: a token issued
        // for the extension may not be redeemed as a browser session either. Presented as the cookie
        // /auth/refresh reads, it is refused rather than rotated.
        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("lm_refresh", extensionRefresh))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        // And it still works on the route it belongs to.
        mvc.perform(post("/api/v1/auth/extension/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(extensionRefresh)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a caller may not open a session claiming to be the extension")
    void aWebCallerCannotClaimTheExtensionsLabel() throws Exception {
        String workspaceOwner = "alok@" + domain;
        verifiedUser("Alok Kumar", workspaceOwner);

        // Were the claimed User-Agent stored verbatim, the client fence would read this family as the
        // extension's — and the session could then never refresh on the route it actually belongs to.
        // A wedged account is a worse failure than a wrong icon, so the claim is refused at write time.
        MvcResult signIn = mvc.perform(post("/api/v1/auth/login")
                        .header("User-Agent", "LightMove Capture (browser extension)")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(workspaceOwner, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookieOf(signIn))
                        .with(csrf()))
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

    /** Login kept whole, because the refresh cookie is on the response and {@code login} drops it. */
    private MvcResult signInRaw(String emailAddress) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(emailAddress, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static Cookie refreshCookieOf(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("lm_refresh");
        assertThat(cookie).as("login should set the refresh cookie").isNotNull();
        return cookie;
    }

    /** A verified user with a workspace — the extension pairs to a mandate-holding account or to none. */
    private String signedInUser(String workspaceName) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), workspaceName);
        return login("alok@" + domain);
    }
}
