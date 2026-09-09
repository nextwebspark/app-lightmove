package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.security.service.CookieAuthorizationRequestStore;
import jakarta.servlet.http.Cookie;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * What the SPA is told to render a button for.
 *
 * <p>The registrations are declared here as properties and nowhere in Java, which is the whole
 * claim: configuring a provider is enough to offer it. LinkedIn's endpoints come from the
 * {@code provider.linkedin} block in {@code application.yml} — if that block were missing, this
 * context would fail to start rather than quietly hand back a broken button. The registrations
 * themselves are {@link ConfiguredOAuthProviders}.
 */
@IntegrationTest
@ConfiguredOAuthProviders
class ConfiguredProvidersTest {

    @Autowired MockMvc mvc;
    @Autowired ClientRegistrationRepository registrations;
    @Autowired CookieAuthorizationRequestStore authorizationRequests;

    @Test
    @DisplayName("every configured registration is offered, by its id")
    void listsEveryConfiguredRegistration() throws Exception {
        mvc.perform(get("/api/v1/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[0]").value("google"))
                .andExpect(jsonPath("$.providers[1]").value("linkedin"));
    }

    @Test
    @DisplayName("LinkedIn's registration carries client_secret_post, which its token endpoint requires")
    void appliesTheConfiguredClientAuthenticationMethod() {
        ClientRegistration linkedin = registrations.findByRegistrationId("linkedin");

        // LinkedIn does not URL-decode HTTP Basic credentials, and Spring encodes them per RFC 6749.
        // A secret containing '/' or '=' therefore authenticates as garbage — "invalid_client", with
        // nothing to say which of the two ends is wrong.
        assertThat(linkedin.getClientAuthenticationMethod())
                .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_POST);
    }

    @Test
    @DisplayName("a registration listed as lacking PKCE and nonce is sent neither, and keeps no verifier")
    void omitsPkceAndNonceForTheRegistrationsThatCannotTakeThem() throws Exception {
        MockHttpServletResponse redirect = redirectFor("linkedin");
        String authorizationUri = redirect.getRedirectedUrl();

        // Not merely absent from the parameter map: absent from the URI the browser actually follows,
        // which is a different thing — the request carries a pre-rendered URI that survives a naive
        // rebuild of its parameters.
        assertThat(authorizationUri).doesNotContain("code_challenge").doesNotContain("nonce");
        assertThat(authorizationUri).contains("state=").contains("client_id=");

        // The stored request is the load-bearing half: the verifier lives there and is replayed at the
        // token exchange, which is where LinkedIn answered invalid_client. A URL clean of the challenge
        // while the attribute survived would fail in exactly the same way, and look fixed.
        OAuth2AuthorizationRequest stored = storedRequestOf(redirect);

        assertThat(stored).isNotNull();
        assertThat(stored.getAttributes())
                .doesNotContainKeys("code_verifier", "nonce")
                .containsEntry("registration_id", "linkedin");
    }

    @Test
    @DisplayName("a provider that implements them keeps PKCE and nonce, so one quirk cannot weaken another")
    void keepsPkceAndNonceForEveryOtherRegistration() throws Exception {
        MockHttpServletResponse redirect = redirectFor("google");

        assertThat(redirect.getRedirectedUrl()).contains("code_challenge").contains("nonce=");

        // The positive half of the round trip, and it has to be asserted rather than inferred: a store
        // that dropped these would look identical to one correctly honouring the quirk lists.
        assertThat(storedRequestOf(redirect).getAttributes())
                .containsKeys("code_verifier", "nonce")
                .containsEntry("registration_id", "google");
    }

    @Test
    @DisplayName("the whole request round-trips, not just the parts a quirk touches")
    void roundTripsEveryFieldTheCallbackNeeds() throws Exception {
        MockHttpServletResponse redirect = redirectFor("google");
        OAuth2AuthorizationRequest stored = storedRequestOf(redirect);

        assertThat(stored.getState()).isEqualTo(stateOf(redirect.getRedirectedUrl()));
        assertThat(stored.getClientId()).isEqualTo("test-google-id");
        assertThat(stored.getRedirectUri()).endsWith("/login/oauth2/code/google");
        assertThat(stored.getScopes()).contains("openid");
    }

    private MockHttpServletResponse redirectFor(String registrationId) throws Exception {
        return mvc.perform(get("/oauth2/authorization/" + registrationId))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse();
    }

    /** Reads back what the redirect stashed in the browser, the way the callback will. */
    private OAuth2AuthorizationRequest storedRequestOf(MockHttpServletResponse redirect) {
        Cookie cookie = redirect.getCookie("lm_oauth_request");
        assertThat(cookie).as("the authorisation request should ride back in a cookie").isNotNull();

        MockHttpServletRequest callback = new MockHttpServletRequest();
        callback.setCookies(cookie);
        callback.setParameter("state", stateOf(redirect.getRedirectedUrl()));
        return authorizationRequests.loadAuthorizationRequest(callback);
    }

    /**
     * Decoded on the way out: the query is percent-encoded and the store compares the raw value, so a
     * state ending in %3D would simply never match.
     */
    private static String stateOf(String authorizationUri) {
        String state = UriComponentsBuilder.fromUriString(authorizationUri)
                .build()
                .getQueryParams()
                .getFirst("state");
        return URLDecoder.decode(state, StandardCharsets.UTF_8);
    }
}
