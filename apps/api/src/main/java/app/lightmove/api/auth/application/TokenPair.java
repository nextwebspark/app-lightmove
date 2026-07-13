package app.lightmove.api.auth.application;

import java.time.Duration;

/**
 * What a successful authentication yields.
 *
 * <p>The two halves leave the server by different routes and that is the point: {@code accessToken}
 * goes in the JSON body for the SPA to hold in memory, while {@code refreshToken} is put straight into
 * an httpOnly cookie by the controller and is never visible to JavaScript. Script that cannot read a
 * token cannot exfiltrate it.
 *
 * <p>{@code toString} is overridden because these end up in log lines and stack traces by accident,
 * and a record's default would print both credentials in full.
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
