package app.lightmove.api.core.security.token;

import java.time.Duration;

/**
 * The access token travels in the JSON body for the SPA's memory, the refresh token only in an
 * httpOnly cookie. {@code toString} is overridden so a log line never prints either.
 */
public record TokenPair(
        String accessToken,
        Duration accessTokenTtl,
        String refreshToken
) {

    @Override
    public String toString() {
        return "TokenPair[accessToken=***, refreshToken=***, ttl=%s]".formatted(accessTokenTtl);
    }
}
