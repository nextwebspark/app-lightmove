package app.lightmove.api.core.security.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Sends a refused OAuth sign-in back to the SPA with a code it can turn into a sentence.
 *
 * <p>The redirect targets the configured web base URL, never this host: Spring's default lands on
 * {@code /login?error} of the API itself, which in development is another port and answers 404 JSON
 * — so the real error never reaches anyone. Success-path refusals and the failure handler both
 * route through here so every failed sign-in ends the same way, carrying an {@link ErrorCode} name
 * and nothing of the provider's own wording.
 *
 * <p>It lands on the same {@code oauth-success-path} a successful sign-in lands on, rather than
 * {@code /login}, so that <b>one</b> SPA route owns both outcomes. That route is what runs inside
 * the sign-in popup, and it can only close the popup and report back for outcomes it is given: a
 * failure sent straight to {@code /login} would render a whole login screen inside a 500×620 window
 * and strand the app behind it. Outside a popup that route forwards to {@code /login?error=} and the
 * user sees exactly what they saw before.
 */
@Component
@RequiredArgsConstructor
public class LoginErrorRedirector {

    private final LightMoveProperties properties;

    public void send(HttpServletResponse response, ErrorCode code) throws IOException {
        String target = UriComponentsBuilder
                .fromUriString(properties.web().baseUrl() + properties.web().oauthSuccessPath())
                .queryParam("error", code.name())
                .build()
                .toUriString();
        response.sendRedirect(target);
    }
}
