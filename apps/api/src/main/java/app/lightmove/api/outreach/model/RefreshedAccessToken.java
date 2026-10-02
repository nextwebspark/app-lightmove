package app.lightmove.api.outreach.model;

import java.time.Duration;

/**
 * A provider's answer to a refresh: the access token, how long it lives, and a new refresh token where the
 * provider rotates them (Microsoft does). {@link #toString()} carries neither token.
 */
public record RefreshedAccessToken(String accessToken, Duration expiresIn, String rotatedRefreshToken) {

    @Override
    public String toString() {
        return "RefreshedAccessToken[expiresIn=" + expiresIn + ", rotated=" + (rotatedRefreshToken != null) + "]";
    }
}
