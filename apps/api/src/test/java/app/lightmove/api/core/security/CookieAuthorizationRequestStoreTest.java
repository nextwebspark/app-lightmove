package app.lightmove.api.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.security.service.CookieAuthorizationRequestStore;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * What survives the trip to the provider and back, when the only carrier is a cookie.
 *
 * <p>{@code StatelessOAuthFlowTest} drives the same store through the real filter chain; this covers
 * what a round trip cannot reach — a cookie from an older format, and one a proxy or a user mangled.
 * Both have to read as <i>the request is not here</i>, because these run inside the security filter
 * chain where {@code GlobalExceptionHandler} does not exist and anything thrown reaches the user as a
 * container error page instead of the sign-in screen.
 */
class CookieAuthorizationRequestStoreTest {

    private static final String COOKIE = "lm_oauth_request";

    private final CookieAuthorizationRequestStore store =
            new CookieAuthorizationRequestStore(JsonMapper.builder().build(), TestAuthSettings.production());

    @Test
    @DisplayName("everything the callback reads off the request comes back")
    void roundTripsTheRequest() {
        OAuth2AuthorizationRequest saved = authorizationRequest(
                Map.of("code_challenge", "challenge", "code_challenge_method", "S256"),
                Map.of("registration_id", "google", "code_verifier", "verifier", "nonce", "nonce-value"));

        OAuth2AuthorizationRequest loaded = reload(saved);

        assertThat(loaded.getState()).isEqualTo(saved.getState());
        assertThat(loaded.getScopes()).isEqualTo(saved.getScopes());
        assertThat(loaded.getRedirectUri()).isEqualTo(saved.getRedirectUri());
        assertThat(loaded.getClientId()).isEqualTo(saved.getClientId());
        assertThat(loaded.getAuthorizationUri()).isEqualTo(saved.getAuthorizationUri());
        assertThat(loaded.getAdditionalParameters()).isEqualTo(saved.getAdditionalParameters());
        assertThat(loaded.getAttributes()).isEqualTo(saved.getAttributes());
    }

    /**
     * The half {@code ProviderQuirkAwareRequestResolver} depends on: a registration listed in
     * {@code pkce-unsupported-registrations} stores no verifier, and a store that invented one — or
     * refused a request without one — would put the LinkedIn {@code invalid_client} bug straight back.
     */
    @Test
    @DisplayName("a request stripped of its verifier and nonce round-trips without them")
    void roundTripsTheAbsenceOfPkceAndNonce() {
        OAuth2AuthorizationRequest loaded = reload(
                authorizationRequest(Map.of(), Map.of("registration_id", "linkedin")));

        assertThat(loaded.getAttributes()).containsOnlyKeys("registration_id");
        assertThat(loaded.getAdditionalParameters()).isEmpty();
    }

    @Test
    @DisplayName("the cookie is not readable by script and comes back on the hop from the provider")
    void writesACookieTheProviderRedirectCanReturnWith() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        store.saveAuthorizationRequest(authorizationRequest(Map.of(), Map.of()),
                new MockHttpServletRequest(), response);

        MockCookie cookie = (MockCookie) response.getCookie(COOKIE);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/login/oauth2");
        assertThat(cookie.getMaxAge()).isEqualTo((int) Duration.ofMinutes(10).toSeconds());
    }

    @Test
    @DisplayName("a request is only returned to the callback that carries its own state")
    void refusesAStateThatIsNotTheStoredOne() {
        MockHttpServletResponse saved = new MockHttpServletResponse();
        store.saveAuthorizationRequest(authorizationRequest(Map.of(), Map.of()),
                new MockHttpServletRequest(), saved);

        assertThat(store.loadAuthorizationRequest(
                callbackCarrying(saved.getCookie(COOKIE), "some-other-state"))).isNull();
    }

    @Test
    @DisplayName("a mangled cookie reads as no request rather than as an error page")
    void refusesACookieItCannotRead() {
        assertThat(store.loadAuthorizationRequest(
                callbackCarrying(new Cookie(COOKIE, "not-base64-and-not-json"), "state-value")))
                .isNull();
    }

    @Test
    @DisplayName("a cookie written by an older format is discarded, not guessed at")
    void refusesACookieFromAnotherFormatVersion() {
        assertThat(store.loadAuthorizationRequest(callbackCarrying(cookieHolding("""
                {"version":0,"authorizationUri":"https://accounts.google.com/o/oauth2/v2/auth",\
                "clientId":"client","redirectUri":"https://app.example/login/oauth2/code/google",\
                "scopes":["openid"],"state":"state-value","parameters":{},"attributes":{}}"""),
                "state-value"))).isNull();
    }

    /**
     * The half a mangled-base64 case cannot reach. Each of these parses as JSON and then dies further
     * in — {@code null} in the version check, a missing {@code scopes} in the rebuild, a missing
     * {@code authorizationUri} in the builder — and each one escaping would be a container error page
     * on the callback instead of the sign-in screen.
     */
    @Test
    @DisplayName("well-formed JSON of the wrong shape is refused too, not thrown")
    void refusesACookieWhoseJsonIsNotAStoredRequest() {
        String[] readableButWrong = {
                "null",
                """
                {"version":1,"state":"state-value"}""",
                """
                {"version":1,"state":"state-value","scopes":[],"parameters":{},"attributes":{}}""",
        };

        for (String payload : readableButWrong) {
            assertThat(store.loadAuthorizationRequest(
                    callbackCarrying(cookieHolding(payload), "state-value")))
                    .as(payload)
                    .isNull();
        }
    }

    /**
     * Deleted whenever one arrived, and only then: a stale cookie would fail every retry until its
     * Max-Age ran out, while a Set-Cookie on a callback that carried none is noise.
     */
    @Test
    @DisplayName("the callback clears the cookie it was given, and sets nothing when it was given none")
    void clearsOnlyACookieThatArrived() {
        MockHttpServletResponse saved = new MockHttpServletResponse();
        store.saveAuthorizationRequest(authorizationRequest(Map.of(), Map.of()),
                new MockHttpServletRequest(), saved);

        MockHttpServletResponse carried = new MockHttpServletResponse();
        store.removeAuthorizationRequest(callbackCarrying(saved.getCookie(COOKIE), "state-value"), carried);
        assertThat(carried.getCookie(COOKIE).getMaxAge()).isZero();

        MockHttpServletResponse bare = new MockHttpServletResponse();
        assertThat(store.removeAuthorizationRequest(new MockHttpServletRequest(), bare)).isNull();
        assertThat(bare.getCookie(COOKIE)).isNull();
    }

    private OAuth2AuthorizationRequest reload(OAuth2AuthorizationRequest request) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        store.saveAuthorizationRequest(request, new MockHttpServletRequest(), response);

        return store.loadAuthorizationRequest(
                callbackCarrying(response.getCookie(COOKIE), request.getState()));
    }

    private static Cookie cookieHolding(String payload) {
        return new Cookie(COOKIE, Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static MockHttpServletRequest callbackCarrying(Cookie cookie, String state) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(cookie);
        request.setParameter("state", state);
        return request;
    }

    private static OAuth2AuthorizationRequest authorizationRequest(Map<String, Object> parameters,
                                                                   Map<String, Object> attributes) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("client-id.apps.googleusercontent.com")
                .redirectUri("https://app.example/login/oauth2/code/google")
                .scopes(Set.of("openid", "profile", "email"))
                .state("state-value")
                .additionalParameters(parameters)
                .attributes(attributes)
                .build();
    }

}
