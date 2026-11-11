package app.lightmove.api.core.security.dto;

/**
 * What a successful authentication returns; the refresh token is deliberately absent (httpOnly cookie only).
 * @param expiresIn seconds, so the SPA can schedule a refresh before the token dies.
 */
public record AuthResponse(
        String accessToken,
        long expiresIn,
        UserResponse user
) {}
