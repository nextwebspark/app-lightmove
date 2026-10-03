package app.lightmove.api.outreach.model;

import java.time.Duration;

/**
 * A token endpoint's answer: the access token, how long it lives, and a refresh token where it sends one — always
 * for a redeemed code, and on a refresh where the provider rotates them (Microsoft does). {@link #toString()}
 * carries neither token.
 */
public record ProviderTokenGrant(String accessToken, Duration expiresIn, String refreshToken) {

    @Override
    public String toString() {
        return "ProviderTokenGrant[expiresIn=" + expiresIn + ", tokens=<redacted>]";
    }
}
