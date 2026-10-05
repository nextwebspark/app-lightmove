package app.lightmove.api.core.security.oauth;

/** How a client came to be registered: by hand or a test, by dynamic registration, or from its client id document. */
public enum OAuthClientSource {
    SEEDED,
    DCR,
    CIMD
}
