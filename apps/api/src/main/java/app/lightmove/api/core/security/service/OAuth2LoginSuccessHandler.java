package app.lightmove.api.core.security.service;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.mfa.MfaChallengeCookieFactory;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.model.MfaChallengeIssued;
import app.lightmove.api.core.security.model.PendingRegistrationIssued;
import app.lightmove.api.core.security.model.ProviderIdentity;
import app.lightmove.api.core.security.model.SignInOutcome;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.model.UserIdentity;
import app.lightmove.api.core.security.registration.PendingRegistrationCookieFactory;
import app.lightmove.api.core.security.registration.PendingRegistrationService;
import app.lightmove.api.core.security.token.RefreshCookieFactory;
import app.lightmove.api.core.security.token.TokenPair;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
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
 * After an identity provider says who you are: the provider authenticates, but we mint our own tokens — or, for a
 * person with a second factor, a challenge for the code, as a right password earns; or, for someone we have never met,
 * a held registration waiting on the terms tick (#928), before any account exists. Nothing here names a provider —
 * every claim read is standard OIDC — so a provider stays a yml block.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final int MAX_AVATAR_URL_LENGTH = 2048;

    /** The SPA's callback page reads this to send the sign-in on to the code step, or to the terms step. */
    static final String CHALLENGE_PARAM = "challenge";
    static final String SECOND_FACTOR_VALUE = "two-factor";
    static final String TERMS_VALUE = "terms";

    private final ProviderSignInService providerSignIn;
    private final PendingRegistrationService pendingRegistrations;
    private final RefreshCookieFactory refreshCookie;
    private final MfaChallengeCookieFactory mfaChallengeCookie;
    private final PendingRegistrationCookieFactory pendingRegistrationCookie;
    private final EmailAddressValidator emailValidator;
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
            ProviderIdentity identity = identify(provider, oidcUser);
            Optional<SignInOutcome> known = transactions.execute(status -> providerSignIn.signInKnown(identity, request));
            if (known.isEmpty()) {
                holdForTerms(identity, response);
                return;
            }
            SignInOutcome outcome = known.get();
            if (outcome instanceof MfaChallengeIssued challenge) {
                // No session yet: the challenge cookie waits for the code, which the opener sends, so nothing
                // crosses between the popup and the app but the outcome.
                response.addHeader(HttpHeaders.SET_COOKIE, mfaChallengeCookie.create(challenge).toString());
                response.sendRedirect(callbackUrl(SECOND_FACTOR_VALUE));
                return;
            }
            TokenPair pair = ((AuthenticatedSession) outcome).tokens();

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
            // Inside the filter chain, where GlobalExceptionHandler does not exist: whatever escapes, still redirect.
            log.error("{} sign-in failed unexpectedly", provider, ex);
            loginErrors.send(response, ErrorCode.OAUTH_FAILED);
        }
    }

    /**
     * Someone we have never met: nothing is created until they tick the terms, so the identity the provider proved is
     * held behind a cookie, as a two-factor challenge is, for the terms step to redeem. The work-email rule is asked
     * here, so a refused address is told so before being asked to agree to anything.
     */
    private void holdForTerms(ProviderIdentity identity, HttpServletResponse response) throws IOException {
        emailValidator.validateWorkEmail(identity.email());
        PendingRegistrationIssued held = pendingRegistrations.hold(identity);
        response.addHeader(HttpHeaders.SET_COOKIE, pendingRegistrationCookie.create(held).toString());
        response.sendRedirect(callbackUrl(TERMS_VALUE));
    }

    private String callbackUrl(String challenge) {
        return UriComponentsBuilder
                .fromUriString(properties.web().baseUrl() + properties.web().oauthSuccessPath())
                .queryParam(CHALLENGE_PARAM, challenge)
                .build()
                .toUriString();
    }

    /** Every refusal of what the provider sent, before anything is read or written. */
    private ProviderIdentity identify(String provider, OidcUser oidcUser) {
        // A registration id colliding with 'LOCAL' would let a federated identity masquerade as a password one.
        if (UserIdentity.LOCAL_PROVIDER.equals(provider)) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS,
                    "Registration id 'local' is reserved for password identities");
        }
        // And one named 'user' would own every picture as the person's own upload, which no sign-in may replace.
        if (User.UPLOADED_AVATAR_SOURCE.equals(provider)) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS,
                    "Registration id 'user' is reserved for a person's own profile photo");
        }

        String email = normalise(oidcUser.getEmail());
        if (email == null || email.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, provider + " returned no email address");
        }
        // An unverified address decides which firm they join and links into an existing account.
        if (!emailProvenBy(provider, oidcUser)) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED,
                    provider + " did not confirm this address is verified");
        }
        return new ProviderIdentity(provider, oidcUser.getSubject(), email, displayName(oidcUser, email),
                usablePictureUrl(oidcUser));
    }

    /**
     * Whether the {@code picture} claim is worth fetching: it is often user-editable, so it must be https and
     * length-capped before our server asks for it. A rejected one falls back to initials.
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
