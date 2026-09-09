package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static app.lightmove.api.auth.OAuthFlowSupport.AUTHORIZATION_REQUEST_COOKIE;
import static app.lightmove.api.auth.OAuthFlowSupport.stateOf;

import app.lightmove.api.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("the callback is completed by the cookie alone, on a request that shares no session")
    void completesTheCallbackFromTheCookieAlone() throws Exception {
        MvcResult authorize = authorize("linkedin");
        Cookie carried = authorize.getResponse().getCookie(AUTHORIZATION_REQUEST_COOKIE);
        assertThat(carried).isNotNull();

        MvcResult callback = mvc.perform(get("/login/oauth2/code/linkedin")
                        .param("state", stateOf(authorize.getResponse().getRedirectedUrl()))
                        .param("error", "access_denied")
                        .cookie(carried))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(callback.getResponse().getRedirectedUrl())
                .endsWith("/auth/callback?error=OAUTH_CANCELLED");

        // Single-use: Spring removes the request before either handler runs, so one clearing covers
        // the success and the failure path alike.
        Cookie cleared = callback.getResponse().getCookie(AUTHORIZATION_REQUEST_COOKIE);
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
                        .param("state", stateOf(authorize.getResponse().getRedirectedUrl()))
                        .param("error", "access_denied"))
                .andReturn();

        assertThat(callback.getResponse().getRedirectedUrl())
                .endsWith("/auth/callback?error=OAUTH_FAILED");
        assertThat(callback.getResponse().getCookie(AUTHORIZATION_REQUEST_COOKIE)).isNull();
    }

    @Test
    @DisplayName("a cookie from another attempt is refused and still cleared, so two sign-ins cannot cross")
    void refusesACookieWhoseStateIsNotTheCallbacksState() throws Exception {
        // One browser, two attempts: the second overwrote the first's cookie, exactly as the session
        // repository overwrote its single attribute. The state check turns that into a clean refusal.
        Cookie other = authorize("google").getResponse().getCookie(AUTHORIZATION_REQUEST_COOKIE);
        MvcResult authorize = authorize("linkedin");

        MvcResult callback = mvc.perform(get("/login/oauth2/code/linkedin")
                        .param("state", stateOf(authorize.getResponse().getRedirectedUrl()))
                        .param("error", "access_denied")
                        .cookie(other))
                .andReturn();

        assertThat(callback.getResponse().getRedirectedUrl())
                .endsWith("/auth/callback?error=OAUTH_FAILED");
        assertThat(callback.getResponse().getCookie(AUTHORIZATION_REQUEST_COOKIE).getMaxAge()).isZero();
    }

    @Test
    @DisplayName("the wiring reaches the response, carrying the values only configuration can supply")
    void writesTheCookieConfigurationDescribes() throws Exception {
        MockCookie cookie = (MockCookie) authorize("google").getResponse()
                .getCookie(AUTHORIZATION_REQUEST_COOKIE);

        // CookieAuthorizationRequestStoreTest owns the builder's own attributes. What only a booted
        // context proves is that the store is wired in at all, and that these two came from yml:
        // Max-Age from lightmove.auth.oauth-request-ttl, Secure from the profile's cookie settings.
        assertThat(cookie).isNotNull();
        assertThat(cookie.getMaxAge()).isEqualTo((int) Duration.ofMinutes(10).toSeconds());
        assertThat(cookie.getSecure()).isTrue();
    }

    private MvcResult authorize(String registrationId) throws Exception {
        return mvc.perform(get("/oauth2/authorization/" + registrationId))
                .andExpect(status().is3xxRedirection())
                .andReturn();
    }

}
