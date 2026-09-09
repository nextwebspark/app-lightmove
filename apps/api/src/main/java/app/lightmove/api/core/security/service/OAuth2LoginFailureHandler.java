package app.lightmove.api.core.security.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * What happens when the provider — or our exchange with it — says no.
 *
 * <p>Spring's default sends the browser to {@code /login?error} on <b>this</b> host. Deployed that is
 * the SPA and merely vague; in development the SPA is on another port, so the user lands on the API's
 * 404 JSON instead of a login screen. Both are fixed by routing failures the same way successes are
 * routed: to the configured web base URL, with a code the SPA can turn into a sentence.
 *
 * <p>The provider's own error is logged and never shown. It quotes {@code redirect_uri},
 * {@code invalid_client} and similar — useful to whoever configured the deployment, meaningless and
 * faintly alarming to the person trying to sign in.
 *
 * <p>The one thing pulled out of that error is whether the person <em>cancelled</em>, which arrives
 * here indistinguishable from a real fault and used to be reported as one.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    /**
     * Not a branch on the provider — a branch on the error code, which is the difference between this
     * and the {@code if (linkedin)} the provider-is-a-yml-block rule forbids. {@code access_denied} is
     * OAuth 2.0's own spelling (RFC 6749 §4.1.2.1) and covers every provider that follows it; the two
     * {@code user_cancelled_*} values are LinkedIn's documented additions, and a provider with a third
     * spelling is another string here rather than another code path.
     */
    private static final Set<String> CANCELLATION_ERROR_CODES =
            Set.of("access_denied", "user_cancelled_login", "user_cancelled_authorize");

    private final LoginErrorRedirector loginErrors;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        if (exception instanceof OAuth2AuthenticationException oauthException) {
            OAuth2Error error = oauthException.getError();

            if (CANCELLATION_ERROR_CODES.contains(error.getErrorCode())) {
                // Not a warning: somebody changed their mind, which is a thing they are allowed to do.
                log.info("OAuth sign-in cancelled by the user: {}", error.getErrorCode());
                loginErrors.send(response, ErrorCode.OAUTH_CANCELLED);
                return;
            }

            log.warn("OAuth sign-in failed: {} {} {}", error.getErrorCode(), error.getDescription(),
                    error.getUri() == null ? "" : error.getUri());
        } else {
            log.warn("OAuth sign-in failed: {}", exception.getMessage());
        }

        loginErrors.send(response, ErrorCode.OAUTH_FAILED);
    }
}
