package app.lightmove.api.outreach.model;

import java.time.Instant;

/**
 * The keys an admin pasted for their own app, as received. {@code clientSecret} blank keeps the one already
 * held for the same client id.
 */
public record OwnAppKeys(String clientId, String clientSecret, String tenantId, Instant secretExpiresAt) {

    @Override
    public String toString() {
        return "OwnAppKeys[clientId=" + clientId + ", tenantId=" + tenantId + ", secretExpiresAt=" + secretExpiresAt
                + ", clientSecret=<redacted>]";
    }
}
