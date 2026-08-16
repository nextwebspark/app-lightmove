package app.lightmove.api.core.security.dto;

/**
 * What a successful authentication returns.
 *
 * <p>The refresh token is <b>deliberately absent</b> — it leaves in an httpOnly cookie script cannot
 * read, and putting it in this body would defeat the point of the cookie.
 *
 * @param expiresIn seconds, so the SPA can schedule a refresh before the token dies.
 */
public record AuthResponse(
        String accessToken,
        long expiresIn,
        UserResponse user
) {}
