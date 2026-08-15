package app.lightmove.api.core.security.service;
import app.lightmove.api.core.security.token.RefreshCookieFactory;
import app.lightmove.api.core.security.repository.UserIdentityRepository;
import app.lightmove.api.core.security.repository.UserRepository;

import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.model.EmailVerifiedEvent;
import app.lightmove.api.core.security.token.TokenPair;
import app.lightmove.api.core.security.token.TokenService;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.model.UserIdentity;
import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * What happens after an identity provider says who you are.
 *
 * <p>The important boundary: <b>the provider authenticates, it does not get to be our session.</b>
 * Once it has proved the user's identity we mint our <i>own</i> access and refresh tokens, so every
 * downstream check — tenant claims, roles, verification, revocation — works identically whether
 * someone signed in with a password, with Google, or with LinkedIn. There is exactly one session
 * model in this system.
 *
 * <p><b>Nothing here names a provider.</b> Which one authenticated is the OAuth registration id,
 * read off the authentication token and stored uppercased on the identity row, and every claim read
 * is standard OIDC ({@code sub}, {@code email}, {@code email_verified}, {@code name},
 * {@code picture}). That is what makes adding a provider a yml block and no code at all — a branch
 * on the provider here would quietly undo it.
 *
 * <p>The work-email rule applies here too, because it is the product rather than an artefact of the
 * password flow: a {@code @gmail.com} federated account is still a {@code @gmail.com} address, and
 * is refused when the blocklist is on. A new user arrives with no workspace and is sent into
 * onboarding, exactly as a password signup is.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final String PRIVACY_POLICY_VERSION = "2026-07-01";

    /** Comfortably above any real CDN URL, and far below what fills a text column. */
    private static final int MAX_AVATAR_URL_LENGTH = 2048;

    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final WorkspaceMemberRepository members;
    private final TokenService tokens;
    private final RefreshCookieFactory refreshCookie;
    private final EmailAddressValidator emailValidator;
    private final AuditService audit;
    private final LightMoveProperties properties;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)
                || !(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            // Overwhelmingly this means a registration configured without the `openid` scope, which
            // yields a plain OAuth2User. Naming what arrived is the difference between a one-line
            // config fix and an afternoon: the browser only ever sees ?error=INVALID_CREDENTIALS.
            log.warn("OAuth sign-in produced no OIDC identity: authentication={}, principal={}",
                    authentication.getClass().getSimpleName(),
                    authentication.getPrincipal() == null
                            ? "null"
                            : authentication.getPrincipal().getClass().getSimpleName());
            redirectWithError(response, ErrorCode.INVALID_CREDENTIALS);
            return;
        }

        // The registration id is the provider's whole identity to us: it names the yml block, the
        // callback path Spring listened on, and the value we store against the account.
        String provider = oauthToken.getAuthorizedClientRegistrationId().toUpperCase(Locale.ROOT);

        try {
            AuthenticatedSession session = transactions
                    .execute(status -> establishSession(provider, oidcUser, request));
            TokenPair pair = session.tokens();

            // The refresh token leaves as an httpOnly cookie, exactly as it does for a password login.
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.create(pair.refreshToken()).toString());

            // The access token rides back in the URL fragment, not the query string. A fragment is
            // never sent to the server, so it stays out of access logs, out of the Referer header, and
            // out of browser history sync. The SPA reads it, stores it in memory, and clears it.
            String target = UriComponentsBuilder
                    .fromUriString(properties.web().baseUrl() + properties.web().oauthSuccessPath())
                    .build()
                    .toUriString() + "#token=" + URLEncoder.encode(pair.accessToken(), StandardCharsets.UTF_8);

            response.sendRedirect(target);

        } catch (ApiException ex) {
            log.info("{} sign-in refused: {} ({})", provider, ex.getCode(), ex.getMessage());
            redirectWithError(response, ex.getCode());
        }
    }

    /**
     * Finds or creates the local user behind a federated account, and issues our tokens.
     *
     * <p>Matched on the provider's stable subject id where possible, and only then on email. The
     * subject id is what actually persists — an email address can be renamed at the provider, and
     * matching solely on it would either lose the link or, worse, attach a provider account to
     * whoever now holds a recycled address.
     */
    // No @Transactional: this is called from onAuthenticationSuccess on this same bean, so the proxy
    // is bypassed and the annotation would be inert — the TransactionTemplate around the call is what
    // actually owns the transaction. See the trap list in CLAUDE.md.
    AuthenticatedSession establishSession(String provider, OidcUser oidcUser, HttpServletRequest request) {
        // 'LOCAL' is not a provider: it marks a password we hashed ourselves, and the identity table is
        // keyed on (provider, providerUserId). A registration id that collided with it would let a
        // federated identity masquerade as a local one.
        if (UserIdentity.LOCAL_PROVIDER.equals(provider)) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS,
                    "Registration id 'local' is reserved for password identities");
        }

        String subject = oidcUser.getSubject();
        String email = normalise(oidcUser.getEmail());

        if (email == null || email.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, provider + " returned no email address");
        }
        // A provider will happily tell us the address is unverified. Believing it anyway would let
        // someone attach an address they do not own — and here that address decides which firm they
        // join, and links them into an account that already exists.
        if (!emailProvenBy(provider, oidcUser)) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED,
                    provider + " did not confirm this address is verified");
        }

        User user = identities.findByProviderAndProviderUserId(provider, subject)
                .flatMap(identity -> users.findById(identity.getUserId()))
                .orElseGet(() -> linkOrRegister(provider, email, subject, oidcUser, request));

        if (!user.getStatus().canAuthenticate()) {
            throw ApiException.of(ErrorCode.ACCOUNT_SUSPENDED);
        }

        refreshProfile(provider, user, oidcUser, email);

        user.recordSuccessfulLogin(Instant.now());
        audit.event(AuthEventType.OAUTH_LOGIN_SUCCEEDED).actor(user.getId()).from(request)
                .detail("provider", provider).record();

        WorkspaceMember membership = members
                .findByUserIdAndStatus(user.getId(), MemberStatus.ACTIVE)
                .orElse(null);

        return tokens.issue(user, membership, request);
    }

    /**
     * No identity from this provider on file. Either this is an existing user signing in with it for
     * the first time — in which case the accounts are linked — or a brand new person.
     */
    private User linkOrRegister(String provider, String email, String subject, OidcUser oidcUser,
                                HttpServletRequest request) {
        return users.findByEmail(email)
                .map(existing -> {
                    // Same address, so the same person: the provider has verified it, and only the
                    // mailbox's owner could have. Linking rather than creating a second account is what
                    // keeps one human from becoming two users with one email — which the schema forbids
                    // anyway.
                    identities.save(UserIdentity.link(existing.getId(), provider, subject, email));

                    // Signing in with the provider proves the same mailbox a verification email exists
                    // to collect, so it redeems a held onboarding exactly as a password reset does.
                    // Publishing is load-bearing: skip it and a user who verifies this way is left on
                    // the "check your inbox" screen with an address that is already verified.
                    boolean verifiedByThisSignIn = !existing.isEmailVerified();
                    existing.markEmailVerified(Instant.now());
                    if (verifiedByThisSignIn) {
                        events.publishEvent(new EmailVerifiedEvent(existing.getId(), request));
                    }

                    log.info("Linked {} account to existing user {}", provider, existing.getId());
                    audit.event(AuthEventType.OAUTH_ACCOUNT_LINKED).actor(existing.getId()).from(request)
                            .detail("provider", provider).record();
                    return existing;
                })
                .orElseGet(() -> register(provider, email, subject, oidcUser, request));
    }

    private User register(String provider, String email, String subject, OidcUser oidcUser,
                          HttpServletRequest request) {
        // The same gate the password signup applies, and the same code path — a federated account is
        // not a way around the work-email rule. A @gmail.com Google account is still @gmail.com.
        String domain = emailValidator.validateWorkEmail(email);

        Instant now = Instant.now();
        User user = users.save(User.registerFederated(
                email,
                displayName(oidcUser, email),
                usablePictureUrl(oidcUser),
                provider,
                now,
                PRIVACY_POLICY_VERSION));

        identities.save(UserIdentity.link(user.getId(), provider, subject, email));

        log.info("Registered user {} via {}, claiming domain {}", user.getId(), provider, domain);
        audit.event(AuthEventType.USER_SIGNED_UP).actor(user.getId()).from(request)
                .detail("provider", provider).detail("domain", domain).record();

        return user;
    }

    /**
     * Re-stamps the picture on every sign-in, because we store the provider's CDN URL rather than a
     * copy of the image and LinkedIn's expire within weeks. A user who signs in keeps a working
     * avatar; one who does not falls back to initials in the SPA.
     *
     * <p>The name is only ever backfilled. It is editable in the product, and a provider must not
     * overwrite what someone typed.
     */
    private void refreshProfile(String provider, User user, OidcUser oidcUser, String email) {
        user.adoptAvatarFrom(provider, usablePictureUrl(oidcUser));
        if (user.getFullName() == null || user.getFullName().isBlank()) {
            user.setFullName(displayName(oidcUser, email));
        }
    }

    /**
     * Whether this provider has actually vouched for the address.
     *
     * <p>{@code email_verified} is optional in OIDC. Spring coerces anything present to a Boolean
     * (its {@code ClaimTypeConverter} runs {@code Boolean.valueOf} over both the id_token and the
     * userinfo response), so the only ambiguous answer is <b>absent</b> — LinkedIn sends nothing at
     * all, which is why demanding {@code Boolean.TRUE} refused every LinkedIn login.
     *
     * <p>Absence is trusted only for the registrations configured as
     * {@code email-verified-optional-registrations}, never by default. The address decides which firm
     * someone joins and links them into an account that may already exist, so "the provider did not
     * say" must not read as "the provider said yes" for a provider nobody has vetted.
     */
    /**
     * Whether a provider's {@code picture} claim is safe to store and hand to every colleague.
     *
     * <p>It is rendered in an {@code <img>} on the roster and on each project's team, so it is a URL
     * the whole workspace fetches. On many identity providers it is also a <i>user-editable profile
     * field</i>: an unfiltered value lets a member point it at a host they control and collect a
     * request — viewer IP, user agent, timestamp — every time a colleague opens the team page.
     * Requiring https keeps it to hosts that at least cannot be tampered with in transit, and the
     * length cap keeps a hostile provider from filling the column. A rejected picture is simply not
     * stored: the SPA falls back to initials, which is a normal state rather than an error.
     */
    private static String usablePictureUrl(OidcUser oidcUser) {
        String picture = oidcUser.getPicture();
        boolean usable = picture != null
                && picture.length() <= MAX_AVATAR_URL_LENGTH
                && picture.regionMatches(true, 0, "https://", 0, "https://".length());

        return usable ? picture : null;
    }

    private boolean emailProvenBy(String provider, OidcUser oidcUser) {
        Object claim = oidcUser.getClaims().get("email_verified");
        if (claim == null) {
            return properties.auth().oauth().emailVerifiedOptionalRegistrations().stream()
                    .anyMatch(registration -> registration.equalsIgnoreCase(provider));
        }
        return Boolean.TRUE.equals(claim);
    }

    /** Sends the browser back to the SPA with a code it can turn into a sentence. */
    private void redirectWithError(HttpServletResponse response, ErrorCode code) throws IOException {
        String target = UriComponentsBuilder
                .fromUriString(properties.web().baseUrl() + "/login")
                .queryParam("error", code.name())
                .build()
                .toUriString();
        response.sendRedirect(target);
    }

    private static String displayName(OidcUser oidcUser, String email) {
        String name = oidcUser.getFullName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return email.substring(0, email.indexOf('@'));
    }

    private static String normalise(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
