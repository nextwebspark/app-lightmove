package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.StubPictureFetcher;
import app.lightmove.api.core.security.constant.PrivacyPolicy;
import app.lightmove.api.core.security.mfa.TestTotp;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.model.UserIdentity;
import app.lightmove.api.core.security.repository.UserIdentityRepository;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.security.service.OAuth2LoginFailureHandler;
import app.lightmove.api.core.security.service.OAuth2LoginSuccessHandler;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

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
@ConfiguredOAuthProviders
class OAuthLoginIntegrationTest extends FlowTestSupport {

    private static final AtomicInteger RUN = new AtomicInteger();
    private static final String PENDING_REGISTRATION = "lm_pending_registration";

    @Autowired OAuth2LoginSuccessHandler handler;
    @Autowired OAuth2LoginFailureHandler failures;
    @Autowired LightMoveProperties properties;
    @Autowired UserRepository users;
    @Autowired UserIdentityRepository identities;
    @Autowired WorkspaceMemberRepository members;
    @Autowired StubPictureFetcher pictures;
    @Autowired @FixtureJdbc JdbcTemplate fixtures;

    @Test
    @DisplayName("a provider named only in configuration registers a user and stores a copy of its picture")
    void registersFromRegistrationIdAlone() throws Exception {
        String email = emailForThisTest();

        MockHttpServletResponse response = signIn("linkedin", "linkedin-subject-1", email,
                Map.of("name", "Alok Kumar", "picture", "https://media.licdn.com/alok.jpg"));

        assertThat(response.getRedirectedUrl()).contains("/auth/callback").contains("#token=");

        User user = users.findByEmail(email).orElseThrow();
        assertThat(user.getFullName()).isEqualTo("Alok Kumar");
        assertThat(user.getAvatarUrl()).startsWith("/api/v1/avatars/");
        assertThat(user.getAvatarSource()).isEqualTo("LINKEDIN");
        assertThat(pictures.fetched()).contains("https://media.licdn.com/alok.jpg");

        UserIdentity identity = identities.findByProviderAndProviderUserId("LINKEDIN", "linkedin-subject-1")
                .orElseThrow();
        assertThat(identity.getUserId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("a first sign-in creates nothing until the terms are ticked, then the tick creates the account once")
    void aFirstSignInWaitsForTheTerms() throws Exception {
        String email = emailForThisTest();

        MockHttpServletResponse held = callback("linkedin", "linkedin-subject-terms", email,
                Map.of("name", "Yara Haddad"));

        assertThat(held.getRedirectedUrl()).endsWith("/auth/callback?challenge=terms").doesNotContain("#token=");
        assertThat(held.getCookie("lm_refresh")).isNull();
        Cookie hold = held.getCookie(PENDING_REGISTRATION);
        assertThat(hold).isNotNull();
        assertThat(hold.isHttpOnly()).isTrue();
        assertThat(hold.getPath()).isEqualTo("/api/v1/auth/oauth/registration");
        assertThat(users.findByEmail(email)).isEmpty();
        assertThat(fixtures.queryForObject("SELECT count(*) FROM app_lm_pending_registration WHERE token_hash = ?",
                Integer.class, Tokens.hash(hold.getValue()))).isEqualTo(1);

        JsonNode shown = body(mvc.perform(get("/api/v1/auth/oauth/registration").cookie(hold))
                .andExpect(status().isOk()).andReturn());
        assertThat(shown.get("provider").asText()).isEqualTo("linkedin");
        assertThat(shown.get("email").asText()).isEqualTo(email);
        assertThat(shown.get("fullName").asText()).isEqualTo("Yara Haddad");

        MvcResult unticked = completeRegistration(hold, false);
        assertThat(unticked.getResponse().getStatus()).isEqualTo(400);
        assertThat(users.findByEmail(email)).isEmpty();

        Instant beforeTick = Instant.now();
        MvcResult ticked = completeRegistration(hold, true);
        assertThat(ticked.getResponse().getStatus()).as(ticked.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(body(ticked).get("accessToken").asText()).isNotBlank();
        assertThat(ticked.getResponse().getCookie("lm_refresh")).isNotNull();
        assertThat(ticked.getResponse().getCookie(PENDING_REGISTRATION).getMaxAge()).isZero();

        User user = users.findByEmail(email).orElseThrow();
        assertThat(user.getFullName()).isEqualTo("Yara Haddad");
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getTermsAcceptedAt()).isAfterOrEqualTo(beforeTick.truncatedTo(ChronoUnit.MILLIS));
        assertThat(user.getPrivacyPolicyVersion()).isEqualTo(PrivacyPolicy.CURRENT_VERSION);
        assertThat(identities.findByProviderAndProviderUserId("LINKEDIN", "linkedin-subject-terms")).isPresent();

        // Single use: the same cookie a second time answers as one that never existed.
        MvcResult replayed = completeRegistration(hold, true);
        assertThat(replayed.getResponse().getStatus()).isEqualTo(410);
        assertThat(codeOf(replayed)).isEqualTo("REGISTRATION_EXPIRED");
    }

    @Test
    @DisplayName("a held registration past its ten minutes, or replaced by a later sign-in, creates nothing")
    void aHeldRegistrationExpiresAndIsReplaced() throws Exception {
        String email = emailForThisTest();
        Cookie first = callback("linkedin", "linkedin-subject-late", email, Map.of()).getCookie(PENDING_REGISTRATION);
        Cookie second = callback("linkedin", "linkedin-subject-late", email, Map.of()).getCookie(PENDING_REGISTRATION);

        MvcResult replaced = completeRegistration(first, true);
        assertThat(codeOf(replaced)).isEqualTo("REGISTRATION_EXPIRED");

        fixtures.update("UPDATE app_lm_pending_registration SET expires_at = now() - interval '1 second' "
                + "WHERE token_hash = ?", Tokens.hash(second.getValue()));
        MvcResult late = completeRegistration(second, true);
        assertThat(late.getResponse().getStatus()).isEqualTo(410);
        mvc.perform(get("/api/v1/auth/oauth/registration").cookie(second)).andExpect(status().isGone());
        assertThat(users.findByEmail(email)).isEmpty();
    }

    @Test
    @DisplayName("the terms step's submit keeps the double-submit check, since its cookie creates an account")
    void theTermsSubmitNeedsTheCsrfHeader() throws Exception {
        String email = emailForThisTest();
        Cookie hold = callback("linkedin", "linkedin-subject-csrf", email, Map.of()).getCookie(PENDING_REGISTRATION);

        mvc.perform(post("/api/v1/auth/oauth/registration").cookie(hold)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("termsAccepted", true))))
                .andExpect(status().isForbidden());
        assertThat(users.findByEmail(email)).isEmpty();
    }

    @Test
    @DisplayName("a consumer address is refused at the callback, before anyone is asked to tick anything")
    void aConsumerAddressIsRefusedBeforeTheTerms() throws Exception {
        MockHttpServletResponse response = callback("google", "google-subject-gmail",
                "someone%d@gmail.com".formatted(RUN.incrementAndGet()), Map.of());

        assertThat(response.getRedirectedUrl()).endsWith("/auth/callback?error=EMAIL_NOT_WORK_ADDRESS");
        assertThat(response.getCookie(PENDING_REGISTRATION)).isNull();
    }

    @Test
    @DisplayName("an unverified account that appeared while the terms waited is linked with the owner's own tick")
    void anUnverifiedAccountMadeMeanwhileTakesTheOwnersTick() throws Exception {
        String email = emailForThisTest();
        Cookie hold = callback("linkedin", "linkedin-subject-claimed", email, Map.of()).getCookie(PENDING_REGISTRATION);
        signup("Claimant", email);
        Instant beforeTick = Instant.now();

        MvcResult ticked = completeRegistration(hold, true);

        assertThat(ticked.getResponse().getStatus()).as(ticked.getResponse().getContentAsString()).isEqualTo(200);
        User owner = users.findByEmail(email).orElseThrow();
        assertThat(owner.hasPassword()).isFalse();
        assertThat(owner.getTermsAcceptedAt()).isAfterOrEqualTo(beforeTick.truncatedTo(ChronoUnit.MILLIS));
        assertThat(owner.owesTermsAcceptance(PrivacyPolicy.CURRENT_VERSION)).isFalse();
        assertThat(fixtures.queryForObject("SELECT count(*) FROM app_lm_audit_event WHERE actor_user_id = ? "
                + "AND event_type = 'USER_TERMS_ACCEPTED'", Integer.class, owner.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("an address that gained an account while the terms waited is linked, not doubled")
    void anAccountMadeMeanwhileIsLinked() throws Exception {
        String email = "meanwhile@" + domain;
        Cookie hold = callback("linkedin", "linkedin-subject-meanwhile", email, Map.of()).getCookie(PENDING_REGISTRATION);
        verifiedUser("Made Meanwhile", email);
        User existing = users.findByEmail(email).orElseThrow();

        MvcResult ticked = completeRegistration(hold, true);

        assertThat(ticked.getResponse().getStatus()).as(ticked.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(identities.findByProviderAndProviderUserId("LINKEDIN", "linkedin-subject-meanwhile")
                .orElseThrow().getUserId()).isEqualTo(existing.getId());
    }

    @Test
    @DisplayName("the provider's picture is copied again on every sign-in, and a re-signed link to the same one keeps it")
    void refreshesTheAvatarOnEverySignIn() throws Exception {
        String email = emailForThisTest();

        signIn("linkedin", "linkedin-subject-2", email, Map.of("picture", "https://cdn.example/first.jpg"));
        String first = avatarOf(email);

        // LinkedIn signs each link afresh: the same photo under a new URL keeps its link, and every cached copy.
        pictures.sameAs("https://cdn.example/first-resigned.jpg", "https://cdn.example/first.jpg");
        signIn("linkedin", "linkedin-subject-2", email, Map.of("picture", "https://cdn.example/first-resigned.jpg"));
        assertThat(avatarOf(email)).isEqualTo(first);

        signIn("linkedin", "linkedin-subject-2", email, Map.of("picture", "https://cdn.example/second.jpg"));
        assertThat(avatarOf(email)).startsWith("/api/v1/avatars/").isNotEqualTo(first);
    }

    @Test
    @DisplayName("a provider that does not answer leaves the picture the person had")
    void keepsThePictureWhenTheProviderIsDown() throws Exception {
        String email = emailForThisTest();
        signIn("linkedin", "linkedin-subject-down", email, Map.of("picture", "https://cdn.example/up.jpg"));
        String held = avatarOf(email);

        pictures.breakDown("https://cdn.example/new.jpg");
        signIn("linkedin", "linkedin-subject-down", email, Map.of("picture", "https://cdn.example/new.jpg"));

        assertThat(avatarOf(email)).isEqualTo(held);
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

        assertThat(other.getRedirectedUrl()).endsWith("/auth/callback?error=EMAIL_NOT_VERIFIED");
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

        assertThat(response.getRedirectedUrl()).endsWith("/auth/callback?error=EMAIL_NOT_VERIFIED");
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

        assertThat(users.findByEmail(email).orElseThrow().getAvatarSource()).isEqualTo("LINKEDIN");
        assertThat(pictures.fetched()).doesNotContain("https://lh3.googleusercontent.com/a/default-user=s96-c");
    }

    @Test
    @DisplayName("a provider fills a picture nobody has supplied yet")
    void adoptsAPictureWhenThereIsNone() throws Exception {
        String email = emailForThisTest();
        signIn("linkedin", "linkedin-subject-nopic", email, Map.of());
        assertThat(users.findByEmail(email).orElseThrow().getAvatarUrl()).isNull();

        signIn("google", "google-subject-nopic", email,
                Map.of("picture", "https://lh3.googleusercontent.com/a/photo.jpg"));

        User user = users.findByEmail(email).orElseThrow();
        assertThat(user.getAvatarUrl()).startsWith("/api/v1/avatars/");
        assertThat(user.getAvatarSource()).isEqualTo("GOOGLE");
    }

    @Test
    @DisplayName("a picture stored before we tracked its source is claimed once, then held")
    void claimsALegacyPictureOnceAndThenHoldsIt() throws Exception {
        String email = emailForThisTest();
        // Every row that predates the avatar_source column looks like this: a picture and no owner.
        User legacy = User.registerFederated(email, "Alok Kumar", Instant.now(), Instant.now(), "2026-07-01");
        legacy.showAvatar(null, "https://cdn.example/legacy.jpg");
        users.save(legacy);

        signIn("google", "google-subject-legacy", email,
                Map.of("picture", "https://lh3.googleusercontent.com/a/claimed.jpg"));
        User claimed = users.findByEmail(email).orElseThrow();
        assertThat(claimed.getAvatarUrl()).startsWith("/api/v1/avatars/");
        assertThat(claimed.getAvatarSource()).isEqualTo("GOOGLE");

        signIn("linkedin", "linkedin-subject-legacy", email,
                Map.of("picture", "https://media.licdn.com/late.jpg"));

        assertThat(avatarOf(email)).isEqualTo(claimed.getAvatarUrl());
    }

    @Test
    @DisplayName("a picture that is not an https URL is left out rather than stored")
    void ignoresAPictureThatIsNotHttps() throws Exception {
        String email = emailForThisTest();

        signIn("linkedin", "linkedin-subject-picture", email, Map.of("picture", "http://cdn.example/x.jpg"));

        // Our server would fetch it, so an unvetted scheme is not asked for at all — the SPA's initials fallback is
        // the designed result.
        assertThat(users.findByEmail(email).orElseThrow().getAvatarUrl()).isNull();
        assertThat(pictures.fetched()).doesNotContain("http://cdn.example/x.jpg");
    }

    @Test
    @DisplayName("a photo the person uploaded is never replaced by a provider's sign-in")
    void anUploadOutlivesEverySignIn() throws Exception {
        String email = "sara@" + domain;
        String token = verifiedUser("Sara G", email);
        String uploaded = body(mvc.perform(multipart(HttpMethod.PUT, "/api/v1/auth/me/avatar")
                        .file(new MockMultipartFile("file", "me.png", "image/png",
                                StubPictureFetcher.pngOf(java.awt.Color.PINK, 120, 120)))
                        .header("Authorization", "Bearer " + token).with(csrf()))
                .andExpect(status().isOk()).andReturn()).get("avatarUrl").asText();

        signIn("google", "google-subject-upload", email,
                Map.of("picture", "https://lh3.googleusercontent.com/a/sara-uploaded.jpg"));

        User user = users.findByEmail(email).orElseThrow();
        assertThat(user.getAvatarUrl()).isEqualTo(uploaded);
        assertThat(user.getAvatarSource()).isEqualTo("USER");
        assertThat(pictures.fetched()).doesNotContain("https://lh3.googleusercontent.com/a/sara-uploaded.jpg");
    }

    @Test
    @DisplayName("a removed photo stays removed: no provider's sign-in brings one back")
    void aRemovalSticks() throws Exception {
        String email = "sara@" + domain;
        String token = verifiedUser("Sara G", email);
        mvc.perform(delete("/api/v1/auth/me/avatar").header("Authorization", "Bearer " + token).with(csrf()))
                .andExpect(status().isOk());

        signIn("linkedin", "linkedin-subject-removed", email, Map.of("picture", "https://media.licdn.com/sara.jpg"));

        assertThat(users.findByEmail(email).orElseThrow().getAvatarUrl()).isNull();
    }

    @Test
    @DisplayName("a registration named 'user' is refused: it would own every picture as the person's own")
    void refusesTheReservedRegistrationId() throws Exception {
        String email = emailForThisTest();

        MockHttpServletResponse response = signIn("user", "user-subject", email, Map.of());

        assertThat(response.getRedirectedUrl()).endsWith("/auth/callback?error=INVALID_CREDENTIALS");
        assertThat(users.findByEmail(email)).isEmpty();
    }

    @Test
    @DisplayName("an explicit email_verified=false is still refused, and creates nothing")
    void refusesAnExplicitDenial() throws Exception {
        String email = emailForThisTest();

        MockHttpServletResponse response = signIn("linkedin", "linkedin-subject-5", email,
                Map.of("email_verified", false));

        assertThat(response.getRedirectedUrl()).endsWith("/auth/callback?error=EMAIL_NOT_VERIFIED");
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
                                {"mode":"COMPANY","name":"Held Firm"}
                                """))
                .andExpect(status().isForbidden());

        String signedIn = accessTokenOf(signIn("linkedin", "linkedin-subject-held", email, Map.of()));

        User user = users.findByEmail(email).orElseThrow();
        assertThat(user.isEmailVerified()).isTrue();

        // And the gate is genuinely open now, on the session the provider sign-in minted.
        mvc.perform(post("/api/v1/onboarding/workspace")
                        .header("Authorization", "Bearer " + signedIn)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"COMPANY","name":"Held Firm"}
                                """))
                .andExpect(status().isCreated());

        assertThat(members.findAllByUserIdAndStatusOrderByJoinedAtAsc(user.getId(), MemberStatus.ACTIVE))
                .hasSize(1);
    }

    @Test
    @DisplayName("a provider proving an address nobody verified shuts out whoever opened the account on it")
    void aProviderProvingTheAddressShutsOutAnUnverifiedClaimant() throws Exception {
        // Someone signs up on the owner's address with a password of their own, and never verifies it.
        String email = "owner@" + domain;
        MvcResult claimant = mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Claimant", "email", email,
                                "password", PASSWORD, "termsAccepted", true))))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie claimantSession = claimant.getResponse().getCookie("lm_refresh");

        signIn("linkedin", "linkedin-subject-owner", email, Map.of());

        MvcResult refusedPassword = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(codeOf(refusedPassword)).isEqualTo("INVALID_CREDENTIALS");
        mvc.perform(post("/api/v1/auth/refresh").cookie(claimantSession).with(csrf()))
                .andExpect(status().isUnauthorized());
        User owner = users.findByEmail(email).orElseThrow();
        assertThat(owner.hasPassword()).isFalse();
        // The claimant ticked the terms, not the owner: the re-acceptance sheet asks the owner on their first screen.
        assertThat(owner.getTermsAcceptedAt()).isNull();
        assertThat(owner.owesTermsAcceptance(PrivacyPolicy.CURRENT_VERSION)).isTrue();
    }

    @Test
    @DisplayName("an unverified claimant's second factor goes too, so the owner is not asked for a code they never had")
    void aProviderProvingTheAddressDropsAnUnverifiedClaimantsFactor() throws Exception {
        String email = "owner2@" + domain;
        signup("Claimant", email);
        enrol(email);

        MockHttpServletResponse owner = signIn("linkedin", "linkedin-subject-owner2", email, Map.of());

        assertThat(owner.getRedirectedUrl()).contains("#token=");
        assertThat(owner.getCookie("lm_mfa_challenge")).isNull();
    }

    @Test
    @DisplayName("linking a provider to a verified account keeps its password")
    void linkingAVerifiedAccountKeepsItsPassword() throws Exception {
        String email = "kept@" + domain;
        verifiedUser("Kept Password", email);

        signIn("linkedin", "linkedin-subject-kept", email, Map.of());

        assertThat(login(email)).isNotBlank();
    }

    @Test
    @DisplayName("a refused sign-in goes back to the SPA's callback route, not to this host's /login")
    void sendsAFailureBackToTheSpa() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        failures.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new OAuth2AuthenticationException(new OAuth2Error("invalid_client", "wrong secret", null)));

        // Spring's default would redirect to /login on the API's own host — which in development is
        // not the SPA at all, and answers the API's 404 JSON. It lands on the callback route rather
        // than the login screen because that route is what runs inside the sign-in popup, and a login
        // screen rendered in a 500x640 window is not an error message anyone can act on.
        assertThat(response.getRedirectedUrl())
                .isEqualTo(properties.web().baseUrl() + "/auth/callback?error=OAUTH_FAILED");
    }

    @Test
    @DisplayName("an unexpected failure during sign-in still lands on the SPA's login screen with a code")
    void sendsAnUnexpectedFailureBackToTheSpa() throws Exception {
        String email = emailForThisTest();

        // A name longer than full_name's 160 characters stands in for anything the sign-in does not
        // guard — concretely the double-callback race, where two concurrent sign-ins both miss the
        // lookups and the loser dies on a unique constraint. The handler runs in the
        // security filter chain, outside GlobalExceptionHandler, so anything that escapes it is a raw
        // container error page rather than a login screen with a sentence on it.
        MockHttpServletResponse response = signIn("linkedin", "linkedin-subject-unexpected", email,
                Map.of("name", "x".repeat(200)));

        assertThat(response.getRedirectedUrl())
                .isEqualTo(properties.web().baseUrl() + "/auth/callback?error=OAUTH_FAILED");
        assertThat(users.findByEmail(email)).isEmpty();
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

    @Test
    @DisplayName("backing out at the provider is reported as a cancellation, not as a failure")
    void reportsAProviderCancellationAsACancellation() throws Exception {
        // What Google and every spec-following provider sends when the user presses Cancel. It used to
        // arrive here indistinguishable from a bad client secret, and was reported as one: "sign-in
        // did not complete, try again" for someone who had simply changed their mind.
        assertThat(redirectAfterFailure("access_denied"))
                .isEqualTo(properties.web().baseUrl() + "/auth/callback?error=OAUTH_CANCELLED");
    }

    @Test
    @DisplayName("a provider's own spelling of a cancellation is read the same way")
    void reportsAProviderSpecificCancellationAsACancellation() throws Exception {
        // LinkedIn's spellings. Matched as error codes, never by asking which provider this was —
        // that would be the one branch the provider-is-a-yml-block rule exists to prevent.
        assertThat(redirectAfterFailure("user_cancelled_login"))
                .isEqualTo(properties.web().baseUrl() + "/auth/callback?error=OAUTH_CANCELLED");
        assertThat(redirectAfterFailure("user_cancelled_authorize"))
                .isEqualTo(properties.web().baseUrl() + "/auth/callback?error=OAUTH_CANCELLED");
    }

    @Test
    @DisplayName("a genuine refusal is still a failure, not a cancellation")
    void keepsARealRefusalAFailure() throws Exception {
        assertThat(redirectAfterFailure("server_error"))
                .isEqualTo(properties.web().baseUrl() + "/auth/callback?error=OAUTH_FAILED");
    }

    private String redirectAfterFailure(String oauthErrorCode) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        failures.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new OAuth2AuthenticationException(new OAuth2Error(oauthErrorCode, null, null)));

        return response.getRedirectedUrl();
    }

    @Test
    @DisplayName("a person with a second factor who signs in with a provider is asked for the code, as after a password")
    void aProviderSignInStillAsksForTheCode() throws Exception {
        String email = "sara@" + domain;
        verifiedUser("Sara G", email);
        String secret = enrol(email);

        MockHttpServletResponse response = signIn("linkedin", "linkedin-subject-mfa", email, Map.of());

        assertThat(response.getRedirectedUrl()).endsWith("/auth/callback?challenge=two-factor");
        assertThat(response.getRedirectedUrl()).doesNotContain("#token=");
        assertThat(response.getCookie("lm_refresh")).isNull();
        Cookie challenge = response.getCookie("lm_mfa_challenge");
        assertThat(challenge).isNotNull();
        assertThat(challenge.isHttpOnly()).isTrue();

        MvcResult signedIn = mvc.perform(post("/api/v1/auth/mfa/verify")
                        .cookie(challenge)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("code", TestTotp.codeAt(secret, Instant.now().plusSeconds(30))))))
                .andReturn();
        assertThat(signedIn.getResponse().getStatus()).as(signedIn.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(body(signedIn).get("accessToken").asText()).isNotBlank();
    }

    @Test
    @DisplayName("a provider sign-in stopped at the code step changes nothing of the account, the picture included")
    void aSignInAtTheCodeStepCopiesNoPicture() throws Exception {
        String email = "sara@" + domain;
        verifiedUser("Sara G", email);
        enrol(email);

        signIn("linkedin", "linkedin-subject-mfa-photo", email,
                Map.of("picture", "https://media.licdn.com/sara-at-the-code-step.jpg"));

        assertThat(pictures.fetched()).doesNotContain("https://media.licdn.com/sara-at-the-code-step.jpg");
        assertThat(avatarOf(email)).isNull();
    }

    /** Turns two-factor on through the password session, answering the factor's secret. */
    private String enrol(String email) throws Exception {
        MvcResult session = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andReturn();
        String token = body(session).get("accessToken").asText();
        Cookie refresh = session.getResponse().getCookie("lm_refresh");
        String secret = body(mvc.perform(post("/api/v1/auth/mfa/setup")
                        .header("Authorization", "Bearer " + token).cookie(refresh).with(csrf()))
                .andReturn()).get("secret").asText();
        MvcResult enabled = mvc.perform(post("/api/v1/auth/mfa/enable")
                        .header("Authorization", "Bearer " + token).cookie(refresh).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "currentPassword", PASSWORD, "code", TestTotp.codeAt(secret, Instant.now())))))
                .andReturn();
        assertThat(enabled.getResponse().getStatus()).as(enabled.getResponse().getContentAsString()).isEqualTo(200);
        return secret;
    }

    private static String accessTokenOf(MockHttpServletResponse response) {
        String redirect = response.getRedirectedUrl();
        assertThat(redirect).contains("#token=");
        return URLDecoder.decode(redirect.substring(redirect.indexOf("#token=") + "#token=".length()),
                StandardCharsets.UTF_8);
    }

    private String avatarOf(String email) {
        return users.findByEmail(email).orElseThrow().getAvatarUrl();
    }

    private String emailForThisTest() {
        return "alok@oauth%d.example".formatted(RUN.incrementAndGet());
    }

    private MockHttpServletResponse signIn(String registrationId, String subject, String email,
                                           Map<String, Object> extraClaims) throws Exception {
        return signIn(registrationId, claimsOf(subject, email, extraClaims));
    }

    /**
     * A sign-in as the person sees it: someone new ticks the terms on the step the callback sends them to, and the
     * provider's callback then signs them in like anyone else. The response is the callback that signed them in.
     */
    private MockHttpServletResponse signIn(String registrationId, Map<String, Object> claims) throws Exception {
        MockHttpServletResponse response = callback(registrationId, claims);
        if (response.getRedirectedUrl() != null && response.getRedirectedUrl().endsWith("?challenge=terms")) {
            MvcResult registered = completeRegistration(response.getCookie(PENDING_REGISTRATION), true);
            assertThat(registered.getResponse().getStatus())
                    .as(registered.getResponse().getContentAsString()).isEqualTo(200);
            return callback(registrationId, claims);
        }
        return response;
    }

    private MvcResult completeRegistration(Cookie held, boolean termsAccepted) throws Exception {
        return mvc.perform(post("/api/v1/auth/oauth/registration")
                        .cookie(held).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("termsAccepted", termsAccepted))))
                .andReturn();
    }

    private static Map<String, Object> claimsOf(String subject, String email, Map<String, Object> extraClaims) {
        Map<String, Object> claims = new HashMap<>(Map.of(
                "sub", subject,
                "email", email,
                "email_verified", true));
        claims.putAll(extraClaims);
        return claims;
    }

    private MockHttpServletResponse callback(String registrationId, String subject, String email,
                                             Map<String, Object> extraClaims) throws Exception {
        return callback(registrationId, claimsOf(subject, email, extraClaims));
    }

    /** The provider's callback alone, as the handler answers it. */
    private MockHttpServletResponse callback(String registrationId, Map<String, Object> claims) throws Exception {
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
