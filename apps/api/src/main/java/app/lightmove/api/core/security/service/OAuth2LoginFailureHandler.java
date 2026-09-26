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
 * Routes a refused OAuth sign-in to the SPA like a success, not to Spring's {@code /login?error} on
 * this host. The provider's error is logged, never shown; only a cancellation is picked out of it.
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
