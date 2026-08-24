package app.lightmove.api.core.security.dto;

/**
 * A browser-extension session: the access token to use now, and the refresh token to come back with.
 *
 * <p>The refresh token is in the body, which {@link AuthResponse} deliberately never does. That is not
 * a relaxation of the same rule but the answer to a different problem: the web app is served from the
 * API's own origin, so its refresh token can live in an httpOnly cookie no script can read. An
 * extension is a different origin and there is no cookie to give it — reaching for the web app's would
 * mean dropping {@code SameSite=Strict} and the path scope that protect it. So the extension holds its
 * own token, in extension-private storage a web page cannot reach, on a much shorter TTL, revocable on
 * its own from Settings → Active sessions.
 *
 * @param expiresIn seconds on the access token, so the extension can refresh before it dies.
 */
public record ExtensionSessionResponse(
        String accessToken,
        long expiresIn,
        String refreshToken,
        UserResponse user
) {}
