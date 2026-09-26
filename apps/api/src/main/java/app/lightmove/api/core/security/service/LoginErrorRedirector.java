package app.lightmove.api.core.security.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Sends a refused OAuth sign-in to the SPA's own {@code oauth-success-path}, never this host, with an
 * {@link ErrorCode} name and none of the provider's wording. One route owns both outcomes because it
 * runs in the sign-in popup; a failure sent to {@code /login} would strand a login screen in the popup.
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
