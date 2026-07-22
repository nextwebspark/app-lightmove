package app.lightmove.api.project.constant;

/**
 * Whether a client has live work with us. Derived from the mandate count, never persisted: a column
 * would drift the moment a project's stage changed. RETAINED once any mandate exists, PROSPECT until then.
 */
public enum ClientType {
    RETAINED,
    PROSPECT
}
