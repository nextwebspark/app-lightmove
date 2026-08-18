package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.model.UserIdentity;
import app.lightmove.api.core.security.repository.UserIdentityRepository;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.security.service.OAuth2LoginFailureHandler;
import app.lightmove.api.core.security.service.OAuth2LoginSuccessHandler;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.TestPropertySource;

/**
 * Signing in with an identity provider, when the provider is only known from configuration.
 *
 * <p>Every case here drives the handler with the registration id {@code linkedin} — an id no Java
 * file mentions — because that is the property the design turns on: a provider is a yml block, and a
 * registration this code has never heard of must sign someone in exactly as Google does.
 *
 * <p>Each test uses its own email domain. Nothing rolls back between tests here, for the same reason
 * as {@link AuthFlowIntegrationTest}.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
@TestPropertySource(properties = "lightmove.auth.oauth.email-verified-optional-registrations=linkedin")
class OAuthLoginIntegrationTest extends FlowTestSupport {

    private static final AtomicInteger RUN = new AtomicInteger();

    @Autowired OAuth2LoginSuccessHandler handler;
    @Autowired OAuth2LoginFailureHandler failures;
    @Autowired LightMoveProperties properties;
    @Autowired UserRepository users;
    @Autowired UserIdentityRepository identities;
    @Autowired WorkspaceMemberRepository members;

    @Test
    @DisplayName("a provider named only in configuration registers a user and stores its picture")
    void registersFromRegistrationIdAlone() throws Exception {
        String email = emailForThisTest();

        MockHttpServletResponse response = signIn("linkedin", "linkedin-subject-1", email,
                Map.of("name", "Alok Kumar", "picture", "https://media.licdn.com/alok.jpg"));

        assertThat(response.getRedirectedUrl()).contains("/auth/callback").contains("#token=");

        User user = users.findByEmail(email).orElseThrow();
        assertThat(user.getFullName()).isEqualTo("Alok Kumar");
        assertThat(user.getAvatarUrl()).isEqualTo("https://media.licdn.com/alok.jpg");

        UserIdentity identity = identities.findByProviderAndProviderUserId("LINKEDIN", "linkedin-subject-1")
                .orElseThrow();
        assertThat(identity.getUserId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("the picture is re-stamped on every sign-in, because provider URLs expire")
    void refreshesTheAvatarOnEverySignIn() throws Exception {
        String email = emailForThisTest();

        signIn("linkedin", "linkedin-subject-2", email, Map.of("picture", "https://cdn.example/first.jpg"));
        signIn("linkedin", "linkedin-subject-2", email, Map.of("picture", "https://cdn.example/second.jpg"));

        assertThat(users.findByEmail(email).orElseThrow().getAvatarUrl())
                .isEqualTo("https://cdn.example/second.jpg");
    }

    @Test
    @DisplayName("an absent email_verified claim is trusted only for a registration configured to allow it")
    void acceptsAnAbsentVerifiedClaimOnlyWhereConfigured() throws Exception {
        String allowed = emailForThisTest();
        MockHttpServletResponse response = signIn("linkedin",
                new HashMap<>(Map.of("sub", "linkedin-subject-4", "email", allowed)));

        assertThat(response.getRedirectedUrl()).contains("#token=");
        assertThat(users.findByEmail(allowed)).isPresent();

        // The same silence from a registration nobody has vetted is refused. Trusting it by default
        // would let an IdP that never checks mailboxes hand over an account on a matching address.
        String refused = emailForThisTest();
        MockHttpServletResponse other = signIn("someotheridp",
                new HashMap<>(Map.of("sub", "other-subject-4", "email", refused)));

        assertThat(other.getRedirectedUrl()).endsWith("/login?error=EMAIL_NOT_VERIFIED");
        assertThat(users.findByEmail(refused)).isEmpty();
    }

    @Test
    @DisplayName("a refused claim never links a provider to an account that already exists")
    void refusesToLinkWhenTheProviderDeniesTheAddress() throws Exception {
        String email = emailForThisTest();
        signIn("linkedin", "linkedin-subject-existing", email, Map.of());
        User existing = users.findByEmail(email).orElseThrow();

        // A second provider claiming the same address, this time admitting it is unverified: the
        // account is already here, so a link would hand it over outright.
        MockHttpServletResponse response = signIn("google", "google-subject-hostile", email,
                Map.of("email_verified", false));

        assertThat(response.getRedirectedUrl()).endsWith("/login?error=EMAIL_NOT_VERIFIED");
        assertThat(identities.findByProviderAndProviderUserId("GOOGLE", "google-subject-hostile")).isEmpty();
        assertThat(users.findById(existing.getId())).isPresent();
    }

    @Test
    @DisplayName("a provider never overwrites a name the user has already given us")
    void doesNotOverwriteAnExistingName() throws Exception {
        String email = emailForThisTest();
        signIn("linkedin", "linkedin-subject-name", email, Map.of("name", "Alok Kumar"));

        signIn("linkedin", "linkedin-subject-name", email, Map.of("name", "alok k (LinkedIn Member)"));

        assertThat(users.findByEmail(email).orElseThrow().getFullName()).isEqualTo("Alok Kumar");
    }

    @Test
    @DisplayName("a second provider never replaces the picture another one supplied")
    void doesNotReplaceAnotherProvidersPicture() throws Exception {
        String email = emailForThisTest();
        signIn("linkedin", "linkedin-subject-photo", email,
                Map.of("picture", "https://media.licdn.com/real-photo.jpg"));

        // Signing in with an account that has no photo of its own: providers send a generated monogram
        // rather than nothing, so without ownership this quietly replaced a real picture.
        signIn("google", "google-subject-photo", email,
                Map.of("picture", "https://lh3.googleusercontent.com/a/default-user=s96-c"));

        assertThat(users.findByEmail(email).orElseThrow().getAvatarUrl())
                .isEqualTo("https://media.licdn.com/real-photo.jpg");
    }

    @Test
    @DisplayName("a provider fills a picture nobody has supplied yet")
    void adoptsAPictureWhenThereIsNone() throws Exception {
        String email = emailForThisTest();
        signIn("linkedin", "linkedin-subject-nopic", email, Map.of());
        assertThat(users.findByEmail(email).orElseThrow().getAvatarUrl()).isNull();

        signIn("google", "google-subject-nopic", email,
                Map.of("picture", "https://lh3.googleusercontent.com/a/photo.jpg"));

        assertThat(users.findByEmail(email).orElseThrow().getAvatarUrl())
                .isEqualTo("https://lh3.googleusercontent.com/a/photo.jpg");
    }

    @Test
    @DisplayName("a picture stored before we tracked its source is claimed once, then held")
    void claimsALegacyPictureOnceAndThenHoldsIt() throws Exception {
        String email = emailForThisTest();
        // Every row that predates the avatar_source column looks like this: a picture and no owner.
        users.save(User.registerFederated(email, "Alok Kumar", "https://cdn.example/legacy.jpg", null,
                Instant.now(), "2026-07-01"));

        signIn("google", "google-subject-legacy", email,
                Map.of("picture", "https://lh3.googleusercontent.com/a/claimed.jpg"));
        assertThat(users.findByEmail(email).orElseThrow().getAvatarUrl())
                .isEqualTo("https://lh3.googleusercontent.com/a/claimed.jpg");

        signIn("linkedin", "linkedin-subject-legacy", email,
                Map.of("picture", "https://media.licdn.com/late.jpg"));

        assertThat(users.findByEmail(email).orElseThrow().getAvatarUrl())
                .isEqualTo("https://lh3.googleusercontent.com/a/claimed.jpg");
    }

    @Test
    @DisplayName("a picture that is not an https URL is left out rather than stored")
    void ignoresAPictureThatIsNotHttps() throws Exception {
        String email = emailForThisTest();

        signIn("linkedin", "linkedin-subject-picture", email, Map.of("picture", "http://cdn.example/x.jpg"));

        // It is handed to every colleague's browser from the roster, so an unvetted scheme is not
        // stored at all — the SPA's initials fallback is the designed result.
        assertThat(users.findByEmail(email).orElseThrow().getAvatarUrl()).isNull();
    }

    @Test
    @DisplayName("an explicit email_verified=false is still refused, and creates nothing")
    void refusesAnExplicitDenial() throws Exception {
        String email = emailForThisTest();

        MockHttpServletResponse response = signIn("linkedin", "linkedin-subject-5", email,
                Map.of("email_verified", false));

        assertThat(response.getRedirectedUrl()).endsWith("/login?error=EMAIL_NOT_VERIFIED");
        assertThat(users.findByEmail(email)).isEmpty();
    }

    /**
     * The provider is a second door through the verification gate, and it has to open it properly: an
     * account stuck at the verify step must be past it afterwards, with no link ever clicked.
     */
    @Test
    @DisplayName("signing in with a provider verifies an account that was stuck at the verify step")
    void providerSignInClearsTheVerificationGate() throws Exception {
        String email = "alok@" + domain;
        String unverified = signup("Alok Kumar", email).get("accessToken").asText();

        // Stuck: the organisation step is closed to them.
        mvc.perform(post("/api/v1/onboarding/workspace")
                        .header("Authorization", "Bearer " + unverified)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Held Firm","companySize":"11-50 people",
                                 "primaryRegion":"GCC","teamFocus":"Executive search"}
                                """))
                .andExpect(status().isForbidden());

        signIn("linkedin", "linkedin-subject-held", email, Map.of());

        User user = users.findByEmail(email).orElseThrow();
        assertThat(user.isEmailVerified()).isTrue();

        // And the gate is genuinely open now, on a session minted after the provider sign-in.
        mvc.perform(post("/api/v1/onboarding/workspace")
                        .header("Authorization", "Bearer " + login(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Held Firm","companySize":"11-50 people",
                                 "primaryRegion":"GCC","teamFocus":"Executive search"}
                                """))
                .andExpect(status().isCreated());

        assertThat(members.findByUserIdAndStatus(user.getId(), MemberStatus.ACTIVE)).isPresent();
    }

    @Test
    @DisplayName("a refused sign-in goes back to the SPA, not to this host's /login")
    void sendsAFailureBackToTheSpa() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        failures.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new OAuth2AuthenticationException(new OAuth2Error("invalid_client", "wrong secret", null)));

        // Spring's default would redirect to /login on the API's own host — which in development is
        // not the SPA at all, and answers the API's 404 JSON.
        assertThat(response.getRedirectedUrl())
                .isEqualTo(properties.web().baseUrl() + "/login?error=OAUTH_FAILED");
    }

    @Test
    @DisplayName("an unexpected failure during sign-in still lands on the SPA's login screen with a code")
    void sendsAnUnexpectedFailureBackToTheSpa() throws Exception {
        String email = emailForThisTest();

        // A name longer than full_name's 160 characters stands in for anything establishSession does
        // not guard — concretely the double-callback race, where two concurrent first sign-ins both
        // miss the lookups and the loser dies on the unique email constraint. The handler runs in the
        // security filter chain, outside GlobalExceptionHandler, so anything that escapes it is a raw
        // container error page rather than a login screen with a sentence on it.
        MockHttpServletResponse response = signIn("linkedin", "linkedin-subject-unexpected", email,
                Map.of("name", "x".repeat(200)));

        assertThat(response.getRedirectedUrl())
                .isEqualTo(properties.web().baseUrl() + "/login?error=OAUTH_FAILED");
        assertThat(users.findByEmail(email)).isEmpty();
    }

    @Test
    @DisplayName("a deployment with no provider configured offers none, so the SPA shows no button")
    void offersNoProvidersWhenNoneAreConfigured() throws Exception {
        mvc.perform(get("/api/v1/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers").isEmpty());
    }

    @Test
    @DisplayName("a second provider on the same address links to the account rather than doubling it")
    void linksASecondProviderToTheSameAccount() throws Exception {
        String email = emailForThisTest();

        signIn("linkedin", "linkedin-subject-6", email, Map.of());
        signIn("google", "google-subject-6", email, Map.of());

        assertThat(users.findByEmail(email)).isPresent();
        assertThat(identities.findByProviderAndProviderUserId("LINKEDIN", "linkedin-subject-6")).isPresent();
        assertThat(identities.findByProviderAndProviderUserId("GOOGLE", "google-subject-6")).isPresent();
    }

    private String emailForThisTest() {
        return "alok@oauth%d.example".formatted(RUN.incrementAndGet());
    }

    private MockHttpServletResponse signIn(String registrationId, String subject, String email,
                                           Map<String, Object> extraClaims) throws Exception {
        Map<String, Object> claims = new HashMap<>(Map.of(
                "sub", subject,
                "email", email,
                "email_verified", true));
        claims.putAll(extraClaims);
        return signIn(registrationId, claims);
    }

    private MockHttpServletResponse signIn(String registrationId, Map<String, Object> claims) throws Exception {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken("id-token", now, now.plus(1, ChronoUnit.HOURS), claims);
        DefaultOidcUser principal = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);

        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal, principal.getAuthorities(), registrationId);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response, authentication);
        return response;
    }
}
