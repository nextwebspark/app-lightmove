package app.lightmove.api.core.security.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The extension presenting its refresh token — for a new session, or to end the one it has.
 *
 * <p>In the body rather than a cookie because the extension has no cookie to send, which is also what
 * makes these routes safe to exempt from CSRF: a cross-site page can cause a browser to attach a
 * cookie it cannot read, but it cannot produce a token it has never seen.
 */
public record ExtensionRefreshRequest(
        @NotBlank(message = "A refresh token is required")
        String refreshToken
) {}
