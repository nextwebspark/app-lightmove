package app.lightmove.api.core.security.dto;

/**
 * A browser-extension session: the access token to use now, and the refresh token to come back with.
 *
 * <p>The refresh token is in the body, which {@link AuthResponse} deliberately never does — the
 * extension is a different origin, so there is no cookie to give it.
 *
 * @param expiresIn seconds on the access token, so the extension can refresh before it dies.
 */
public record ExtensionSessionResponse(
        String accessToken,
        long expiresIn,
        String refreshToken,
        UserResponse user
) {}
