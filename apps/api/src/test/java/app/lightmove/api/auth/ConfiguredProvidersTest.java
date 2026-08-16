package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * What the SPA is told to render a button for.
 *
 * <p>The registrations are declared here as properties and nowhere in Java, which is the whole
 * claim: configuring a provider is enough to offer it. LinkedIn's endpoints come from the
 * {@code provider.linkedin} block in {@code application.yml} — if that block were missing, this
 * context would fail to start rather than quietly hand back a broken button.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-google-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-secret",
        "spring.security.oauth2.client.registration.linkedin.client-id=test-linkedin-id",
        "spring.security.oauth2.client.registration.linkedin.client-secret=test-linkedin-secret",
        "spring.security.oauth2.client.registration.linkedin.scope=openid,profile,email",
        "spring.security.oauth2.client.registration.linkedin.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.linkedin.client-authentication-method=client_secret_post",
        "spring.security.oauth2.client.registration.linkedin.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "lightmove.auth.oauth.pkce-unsupported-registrations=linkedin",
        "lightmove.auth.oauth.nonce-unsupported-registrations=linkedin",
})
class ConfiguredProvidersTest {

    @Autowired MockMvc mvc;
    @Autowired ClientRegistrationRepository registrations;

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
        MockHttpSession session = new MockHttpSession();
        String authorizationUri = mvc.perform(get("/oauth2/authorization/linkedin").session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();

        // Not merely absent from the parameter map: absent from the URI the browser actually follows,
        // which is a different thing — the request carries a pre-rendered URI that survives a naive
        // rebuild of its parameters.
        assertThat(authorizationUri).doesNotContain("code_challenge").doesNotContain("nonce");
        assertThat(authorizationUri).contains("state=").contains("client_id=");

        // The stored request is the load-bearing half: the verifier lives there and is replayed at the
        // token exchange, which is where LinkedIn answered invalid_client. A URL clean of the challenge
        // while the attribute survived would fail in exactly the same way, and look fixed.
        OAuth2AuthorizationRequest stored = new HttpSessionOAuth2AuthorizationRequestRepository()
                .loadAuthorizationRequest(requestCarrying(session, authorizationUri));

        assertThat(stored).isNotNull();
        assertThat(stored.getAttributes()).doesNotContainKeys("code_verifier", "nonce");
    }

    /** The stored request is keyed on `state`, so the lookup needs the value the redirect carried. */
    private static MockHttpServletRequest requestCarrying(MockHttpSession session, String authorizationUri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        // Decoded on the way in: the query is percent-encoded and the repository keys on the raw
        // value, so a state ending in %3D would simply never be found.
        String state = UriComponentsBuilder.fromUriString(authorizationUri)
                .build()
                .getQueryParams()
                .getFirst("state");
        request.setParameter("state", URLDecoder.decode(state, StandardCharsets.UTF_8));
        return request;
    }

    @Test
    @DisplayName("a provider that implements them keeps PKCE and nonce, so one quirk cannot weaken another")
    void keepsPkceAndNonceForEveryOtherRegistration() throws Exception {
        String authorizationUri = redirectFor("google");

        assertThat(authorizationUri).contains("code_challenge").contains("nonce=");
    }

    private String redirectFor(String registrationId) throws Exception {
        return mvc.perform(get("/oauth2/authorization/" + registrationId))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
    }
}
