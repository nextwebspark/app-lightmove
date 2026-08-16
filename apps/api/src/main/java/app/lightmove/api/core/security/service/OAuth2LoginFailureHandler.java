package app.lightmove.api.core.security.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final LoginErrorRedirector loginErrors;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        if (exception instanceof OAuth2AuthenticationException oauthException) {
            OAuth2Error error = oauthException.getError();
            log.warn("OAuth sign-in failed: {} {} {}", error.getErrorCode(), error.getDescription(),
                    error.getUri() == null ? "" : error.getUri());
        } else {
            log.warn("OAuth sign-in failed: {}", exception.getMessage());
        }

        loginErrors.send(response, ErrorCode.OAUTH_FAILED);
    }
}
