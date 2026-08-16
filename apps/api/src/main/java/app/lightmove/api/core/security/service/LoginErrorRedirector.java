package app.lightmove.api.core.security.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Sends a refused OAuth sign-in back to the SPA's login screen with a code it can turn into a
 * sentence.
 *
 * <p>The redirect targets the configured web base URL, never this host: Spring's default lands on
 * {@code /login?error} of the API itself, which in development is another port and answers 404 JSON
 * — so the real error never reaches anyone. Success-path refusals and the failure handler both
 * route through here so every failed sign-in ends the same way: on the login screen, carrying an
 * {@link ErrorCode} name and nothing of the provider's own wording.
 */
@Component
@RequiredArgsConstructor
public class LoginErrorRedirector {

    private final LightMoveProperties properties;

    public void send(HttpServletResponse response, ErrorCode code) throws IOException {
        String target = UriComponentsBuilder
                .fromUriString(properties.web().baseUrl() + "/login")
                .queryParam("error", code.name())
                .build()
                .toUriString();
        response.sendRedirect(target);
    }
}
