package app.lightmove.api.outreach.model;

import java.time.LocalDate;

/**
 * The keys an admin pasted for their own app, as received. {@code clientSecret} blank keeps the one already
 * held for the same client id.
 */
public record OwnAppKeys(String clientId, String clientSecret, String tenantId, LocalDate secretExpiresOn) {

    public boolean isEmpty() {
        return isBlank(clientId) && isBlank(clientSecret) && isBlank(tenantId) && secretExpiresOn == null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public String toString() {
        return "OwnAppKeys[clientId=" + clientId + ", tenantId=" + tenantId + ", secretExpiresOn=" + secretExpiresOn
                + ", clientSecret=<redacted>]";
    }
}
