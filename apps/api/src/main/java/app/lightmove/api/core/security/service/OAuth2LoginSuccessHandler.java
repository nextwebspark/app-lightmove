package app.lightmove.api.core.security.service;
import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.constant.PrivacyPolicy;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.model.UserIdentity;
import app.lightmove.api.core.security.repository.UserIdentityRepository;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.security.token.RefreshCookieFactory;
import app.lightmove.api.core.security.token.TokenPair;
import app.lightmove.api.core.security.token.TokenService;
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
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * After an identity provider says who you are: the provider authenticates, but we mint our own tokens.
 * Nothing here names a provider — every claim read is standard OIDC — so a provider stays a yml block.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final int MAX_AVATAR_URL_LENGTH = 2048;

    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final WorkspaceSelection selection;
    private final TokenService tokens;
    private final RefreshCookieFactory refreshCookie;
    private final EmailAddressValidator emailValidator;
    private final AuditService audit;
    private final LightMoveProperties properties;
    private final TransactionTemplate transactions;
    private final LoginErrorRedirector loginErrors;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)
                || !(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            // Usually a registration without the `openid` scope; the browser only sees INVALID_CREDENTIALS.
            log.warn("OAuth sign-in produced no OIDC identity: authentication={}, principal={}",
                    authentication.getClass().getSimpleName(),
                    authentication.getPrincipal() == null
                            ? "null"
                            : authentication.getPrincipal().getClass().getSimpleName());
            loginErrors.send(response, ErrorCode.INVALID_CREDENTIALS);
            return;
        }

        String provider = oauthToken.getAuthorizedClientRegistrationId().toUpperCase(Locale.ROOT);

        try {
            AuthenticatedSession session = transactions
                    .execute(status -> establishSession(provider, oidcUser, request));
            TokenPair pair = session.tokens();

            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.create(pair.refreshToken()).toString());

            // In the fragment, not the query: it is never sent to a server, so stays out of logs and Referer.
            String target = UriComponentsBuilder
                    .fromUriString(properties.web().baseUrl() + properties.web().oauthSuccessPath())
                    .build()
                    .toUriString() + "#token=" + URLEncoder.encode(pair.accessToken(), StandardCharsets.UTF_8);

            response.sendRedirect(target);

        } catch (ApiException ex) {
            log.info("{} sign-in refused: {} ({})", provider, ex.getCode(), ex.getMessage());
            loginErrors.send(response, ex.getCode());
        } catch (Exception ex) {
            // Inside the filter chain, where GlobalExceptionHandler does not exist. Two concurrent first
            // sign-ins race into register() and the loser dies on the unique email; still redirect.
            log.error("{} sign-in failed unexpectedly", provider, ex);
            loginErrors.send(response, ErrorCode.OAUTH_FAILED);
        }
    }

    /**
     * Finds or creates the local user and issues our tokens. Matched on the provider's subject first:
     * matching on email alone would attach the account to whoever now holds a recycled address.
     */
    // No @Transactional: a self-call bypasses the proxy; the TransactionTemplate owns the transaction.
    AuthenticatedSession establishSession(String provider, OidcUser oidcUser, HttpServletRequest request) {
        // A registration id colliding with 'LOCAL' would let a federated identity masquerade as a password one.
        if (UserIdentity.LOCAL_PROVIDER.equals(provider)) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS,
                    "Registration id 'local' is reserved for password identities");
        }

        String subject = oidcUser.getSubject();
        String email = normalise(oidcUser.getEmail());

        if (email == null || email.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, provider + " returned no email address");
        }
        // An unverified address decides which firm they join and links into an existing account.
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

        return tokens.issue(user, selection.signIn(user), request);
    }

    private User linkOrRegister(String provider, String email, String subject, OidcUser oidcUser,
                                HttpServletRequest request) {
        return users.findByEmail(email)
                .map(existing -> {
                    // The provider verified the address, so only the mailbox's owner could be here.
                    identities.save(UserIdentity.link(existing.getId(), provider, subject, email));

                    existing.markEmailVerified(Instant.now());

                    log.info("Linked {} account to existing user {}", provider, existing.getId());
                    audit.event(AuthEventType.OAUTH_ACCOUNT_LINKED).actor(existing.getId()).from(request)
                            .detail("provider", provider).record();
                    return existing;
                })
                .orElseGet(() -> register(provider, email, subject, oidcUser, request));
    }

    private User register(String provider, String email, String subject, OidcUser oidcUser,
                          HttpServletRequest request) {
        // A federated account is no way around the work-email rule.
        String domain = emailValidator.validateWorkEmail(email);

        Instant now = Instant.now();
        User user = users.save(User.registerFederated(
                email,
                displayName(oidcUser, email),
                usablePictureUrl(oidcUser),
                provider,
                now,
                PrivacyPolicy.CURRENT_VERSION));

        identities.save(UserIdentity.link(user.getId(), provider, subject, email));

        log.info("Registered user {} via {}, claiming domain {}", user.getId(), provider, domain);
        audit.event(AuthEventType.USER_SIGNED_UP).actor(user.getId()).from(request)
                .detail("provider", provider).detail("domain", domain).record();

        return user;
    }

    /** Re-stamps the picture (provider CDN URLs expire); the name is only backfilled, never overwritten. */
    private void refreshProfile(String provider, User user, OidcUser oidcUser, String email) {
        user.adoptAvatarFrom(provider, usablePictureUrl(oidcUser));
        if (user.getFullName() == null || user.getFullName().isBlank()) {
            user.setFullName(displayName(oidcUser, email));
        }
    }

    /**
     * Whether the {@code picture} claim is safe to store: it is often user-editable and every colleague's
     * browser fetches it, so it must be https and length-capped. A rejected one falls back to initials.
     */
    private static String usablePictureUrl(OidcUser oidcUser) {
        String picture = oidcUser.getPicture();
        boolean usable = picture != null
                && picture.length() <= MAX_AVATAR_URL_LENGTH
                && picture.regionMatches(true, 0, "https://", 0, "https://".length());

        return usable ? picture : null;
    }

    /**
     * Whether the provider vouched for the address. {@code email_verified} may be absent (LinkedIn sends
     * none); absence is trusted only for {@code email-verified-optional-registrations}, never by default.
     */
    private boolean emailProvenBy(String provider, OidcUser oidcUser) {
        Object claim = oidcUser.getClaims().get("email_verified");
        if (claim == null) {
            return properties.auth().oauth().emailVerifiedOptionalRegistrations().stream()
                    .anyMatch(registration -> registration.equalsIgnoreCase(provider));
        }
        return Boolean.TRUE.equals(claim);
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
