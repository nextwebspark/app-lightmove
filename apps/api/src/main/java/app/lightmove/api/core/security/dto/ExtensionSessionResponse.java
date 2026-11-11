package app.lightmove.api.core.security.dto;

/**
 * A browser-extension session. The refresh token rides in the body, unlike {@link AuthResponse}: the extension is
 * another origin, with no cookie to give it.
 *
 * @param expiresIn seconds on the access token, so the extension can refresh before it dies.
 */
public record ExtensionSessionResponse(
        String accessToken,
        long expiresIn,
        String refreshToken,
        UserResponse user
) {}
