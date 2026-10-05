package app.lightmove.api.core.security.apikey;

/** Where a key stands at the moment it is read; derived, never stored. */
public enum ApiKeyStatus {
    ACTIVE,
    EXPIRED,
    REVOKED
}
