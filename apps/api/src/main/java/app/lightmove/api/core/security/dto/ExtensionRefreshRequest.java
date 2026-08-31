package app.lightmove.api.core.security.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The extension presenting its refresh token — for a new session, or to end the one it has.
 *
 * <p>In the body, which is why these routes are CSRF-exempt: there is no cookie for a cross-site page
 * to cause the browser to attach.
 */
public record ExtensionRefreshRequest(
        @NotBlank(message = "A refresh token is required")
        String refreshToken
) {}
