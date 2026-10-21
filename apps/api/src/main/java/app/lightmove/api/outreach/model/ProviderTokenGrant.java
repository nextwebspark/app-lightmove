package app.lightmove.api.outreach.model;

import java.time.Duration;
import java.util.Set;

/**
 * A token endpoint's answer: the access token, how long it lives, and a refresh token where it sends one — always
 * for a redeemed code, and on a refresh where the provider rotates them (Microsoft does). {@code scopes} is what the
 * person actually granted, which on Google's granular consent may be less than was asked. {@link #toString()}
 * carries neither token.
 */
public record ProviderTokenGrant(String accessToken, Duration expiresIn, String refreshToken, Set<String> scopes) {

    public ProviderTokenGrant {
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    public ProviderTokenGrant(String accessToken, Duration expiresIn, String refreshToken) {
        this(accessToken, expiresIn, refreshToken, Set.of());
    }

    @Override
    public String toString() {
        return "ProviderTokenGrant[expiresIn=" + expiresIn + ", tokens=<redacted>]";
    }
}
