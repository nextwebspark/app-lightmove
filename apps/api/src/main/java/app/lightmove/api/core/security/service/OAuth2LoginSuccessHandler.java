package app.lightmove.api.core.security.service;
import app.lightmove.api.core.security.token.RefreshCookieFactory;
import app.lightmove.api.core.security.repository.UserIdentityRepository;
import app.lightmove.api.core.security.repository.UserRepository;

import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.token.TokenPair;
import app.lightmove.api.core.security.token.TokenService;
import app.lightmove.api.core.security.constant.AuthProvider;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.model.UserIdentity;
import app.lightmove.api.core.audit.constant.AuditEventType;
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
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * What happens after Google says who you are.
 *
 * <p>The important boundary: <b>Google authenticates, it does not get to be our session.</b> Once it
 * has proved the user's identity we mint our <i>own</i> access and refresh tokens, so every downstream
 * check — tenant claims, roles, verification, revocation — works identically whether someone signed in
 * with a password or with Google. There is exactly one session model in this system.
 *
 * <p>The work-email rule applies here too, because it is the product rather than an artefact of the
 * password flow: a {@code @gmail.com} Google account is still a {@code @gmail.com} address, and is
 * refused. A new user arrives with no workspace and is sent into onboarding, exactly as a password
 * signup is.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final String PRIVACY_POLICY_VERSION = "2026-07-01";

    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final WorkspaceMemberRepository members;
    private final TokenService tokens;
    private final RefreshCookieFactory refreshCookie;
    private final EmailAddressValidator emailValidator;
    private final AuditService audit;
    private final LightMoveProperties properties;
    private final TransactionTemplate transactions;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            redirectWithError(response, ErrorCode.INVALID_CREDENTIALS);
            return;
        }

        try {
            AuthenticatedSession session = transactions.execute(status -> establishSession(oidcUser, request));
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
            log.info("Google sign-in refused: {} ({})", ex.getCode(), ex.getMessage());
            redirectWithError(response, ex.getCode());
        }
    }

    /**
     * Finds or creates the local user behind a Google account, and issues our tokens.
     *
     * <p>Matched on Google's stable subject id where possible, and only then on email. The subject id
     * is what actually persists — an email address can be renamed at the provider, and matching solely
     * on it would either lose the link or, worse, attach a Google account to whoever now holds a
     * recycled address.
     */
    @Transactional
    AuthenticatedSession establishSession(OidcUser oidcUser, HttpServletRequest request) {
        String googleSubject = oidcUser.getSubject();
        String email = normalise(oidcUser.getEmail());

        if (email == null || email.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Google returned no email address");
        }
        // Google will happily tell us the address is unverified. Believing it anyway would let someone
        // attach an address they do not own — and here that address decides which firm they join.
        if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED, "Google reports this address as unverified");
        }

        User user = identities.findByProviderAndProviderUserId(AuthProvider.GOOGLE, googleSubject)
                .flatMap(identity -> users.findById(identity.getUserId()))
                .orElseGet(() -> linkOrRegister(email, googleSubject, oidcUser, request));

        if (!user.getStatus().canAuthenticate()) {
            throw ApiException.of(ErrorCode.ACCOUNT_SUSPENDED);
        }

        user.recordSuccessfulLogin(Instant.now());
        audit.event(AuditEventType.OAUTH_LOGIN_SUCCEEDED).actor(user.getId()).from(request)
                .detail("provider", "GOOGLE").record();

        WorkspaceMember membership = members
                .findByUserIdAndStatus(user.getId(), MemberStatus.ACTIVE)
                .orElse(null);

        return tokens.issue(user, membership, request);
    }

    /**
     * No Google identity on file. Either this is an existing password user signing in with Google for
     * the first time — in which case the accounts are linked — or a brand new person.
     */
    private User linkOrRegister(String email, String googleSubject, OidcUser oidcUser,
                                HttpServletRequest request) {
        return users.findByEmail(email)
                .map(existing -> {
                    // Same address, so the same person: Google has verified it, and only the mailbox's
                    // owner could have. Linking rather than creating a second account is what keeps one
                    // human from becoming two users with one email — which the schema forbids anyway.
                    identities.save(UserIdentity.link(existing.getId(), AuthProvider.GOOGLE, googleSubject, email));
                    existing.markEmailVerified(Instant.now());

                    log.info("Linked Google account to existing user {}", existing.getId());
                    audit.event(AuditEventType.OAUTH_ACCOUNT_LINKED).actor(existing.getId()).from(request)
                            .detail("provider", "GOOGLE").record();
                    return existing;
                })
                .orElseGet(() -> register(email, googleSubject, oidcUser, request));
    }

    private User register(String email, String googleSubject, OidcUser oidcUser, HttpServletRequest request) {
        // The same gate the password signup applies, and the same code path — a Google account is not a
        // way around the work-email rule. A @gmail.com Google account is still a @gmail.com address.
        String domain = emailValidator.validateWorkEmail(email);

        Instant now = Instant.now();
        User user = users.save(User.registerFederated(
                email,
                displayName(oidcUser, email),
                oidcUser.getPicture(),
                now,
                PRIVACY_POLICY_VERSION));

        identities.save(UserIdentity.link(user.getId(), AuthProvider.GOOGLE, googleSubject, email));

        log.info("Registered user {} via Google, claiming domain {}", user.getId(), domain);
        audit.event(AuditEventType.USER_SIGNED_UP).actor(user.getId()).from(request)
                .detail("provider", "GOOGLE").detail("domain", domain).record();

        return user;
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
