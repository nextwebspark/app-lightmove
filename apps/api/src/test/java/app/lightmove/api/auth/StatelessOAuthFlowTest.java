package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The whole handshake, carried by a cookie and nothing else.
 *
 * <p>Nothing here shares a session between the redirect out to the provider and the callback back —
 * that is the point. Spring's default parks the {@code state}, the verifier and the nonce in an
 * in-memory {@code HttpSession}, so a callback that lands on another Cloud Run instance, or after a
 * restart, or past a session timeout, finds nothing and is reported to the user as a failed sign-in.
 *
 * <p><b>Why every callback here carries {@code error=} rather than {@code code=}.</b> A callback with a
 * code goes on to a real token exchange against the provider, and no test may open a socket. The
 * provider's own error is checked before that exchange and after the request has been reconstituted,
 * so {@code OAUTH_CANCELLED} is reachable <i>only</i> when the cookie was found, decoded, matched on
 * state, and yielded a registration id that resolved. Every way this could break — no cookie, an
 * unreadable one, a lost state, a lost registration id — lands on {@code authorization_request_not_found}
 * and comes back as {@code OAUTH_FAILED}. Two codes, one bit, no stubs.
 */
@IntegrationTest
@ConfiguredOAuthProviders
class StatelessOAuthFlowTest {

    private static final String COOKIE = "lm_oauth_request";

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("the callback is completed by the cookie alone, on a request that shares no session")
    void completesTheCallbackFromTheCookieAlone() throws Exception {
        MvcResult authorize = authorize("linkedin");
        Cookie carried = authorize.getResponse().getCookie(COOKIE);
        assertThat(carried).isNotNull();

        MvcResult callback = mvc.perform(get("/login/oauth2/code/linkedin")
                        .param("state", stateOf(authorize))
                        .param("error", "access_denied")
                        .cookie(carried))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(callback.getResponse().getRedirectedUrl())
                .endsWith("/auth/callback?error=OAUTH_CANCELLED");

        // Single-use: Spring removes the request before either handler runs, so one clearing covers
        // the success and the failure path alike.
        Cookie cleared = callback.getResponse().getCookie(COOKIE);
        assertThat(cleared).isNotNull();
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();

        // Neither leg may create one — this is what "no session is needed" means, asserted rather
        // than asserted about.
        assertThat(authorize.getRequest().getSession(false)).isNull();
        assertThat(callback.getRequest().getSession(false)).isNull();
    }

    @Test
    @DisplayName("without the cookie the same callback is refused, and nothing is set to clear")
    void refusesACallbackCarryingNoCookie() throws Exception {
        MvcResult authorize = authorize("linkedin");

        MvcResult callback = mvc.perform(get("/login/oauth2/code/linkedin")
                        .param("state", stateOf(authorize))
                        .param("error", "access_denied"))
                .andReturn();

        assertThat(callback.getResponse().getRedirectedUrl())
                .endsWith("/auth/callback?error=OAUTH_FAILED");
        assertThat(callback.getResponse().getCookie(COOKIE)).isNull();
    }

    @Test
    @DisplayName("a cookie from another attempt is refused and still cleared, so two sign-ins cannot cross")
    void refusesACookieWhoseStateIsNotTheCallbacksState() throws Exception {
        // One browser, two attempts: the second overwrote the first's cookie, exactly as the session
        // repository overwrote its single attribute. The state check turns that into a clean refusal.
        Cookie other = authorize("google").getResponse().getCookie(COOKIE);
        MvcResult authorize = authorize("linkedin");

        MvcResult callback = mvc.perform(get("/login/oauth2/code/linkedin")
                        .param("state", stateOf(authorize))
                        .param("error", "access_denied")
                        .cookie(other))
                .andReturn();

        assertThat(callback.getResponse().getRedirectedUrl())
                .endsWith("/auth/callback?error=OAUTH_FAILED");
        assertThat(callback.getResponse().getCookie(COOKIE).getMaxAge()).isZero();
    }

    @Test
    @DisplayName("the cookie is written so the hop back from the provider can carry it")
    void writesACookieTheProviderRedirectCanReturnWith() throws Exception {
        MockCookie cookie = (MockCookie) authorize("google").getResponse().getCookie(COOKIE);

        // It carries the code_verifier and the nonce, so script must never reach it.
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/login/oauth2");
        assertThat(cookie.getMaxAge()).isEqualTo((int) Duration.ofMinutes(10).toSeconds());

        // Strict is withheld on the top-level cross-site GET the provider redirects with, and the
        // callback then fails as authorization_request_not_found — the bug this cookie exists to fix.
        assertThat(cookie.getSameSite()).isEqualTo("Lax");

        // The test profile overrides no cookie settings, so this is application.yml's production value
        // reaching the new cookie — which is what pins that local and e2e's `secure: false` does too.
        assertThat(cookie.getSecure()).isTrue();
    }

    private MvcResult authorize(String registrationId) throws Exception {
        return mvc.perform(get("/oauth2/authorization/" + registrationId))
                .andExpect(status().is3xxRedirection())
                .andReturn();
    }

    /** Percent-decoded: the store compares the raw value, and a state ending in %3D would never match. */
    private static String stateOf(MvcResult authorize) {
        String state = UriComponentsBuilder.fromUriString(authorize.getResponse().getRedirectedUrl())
                .build()
                .getQueryParams()
                .getFirst("state");
        return URLDecoder.decode(state, StandardCharsets.UTF_8);
    }
}
