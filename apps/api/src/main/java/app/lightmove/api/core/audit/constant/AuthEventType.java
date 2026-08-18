package app.lightmove.api.core.audit.constant;

/**
 * Auth-domain audit events: registration, session, tokens, credentials, and federation. See
 * {@link AuditEventType} for why the ledger's event set is split this way.
 */
public enum AuthEventType implements AuditEventType {

    // Registration
    USER_SIGNED_UP,
    EMAIL_VERIFICATION_SENT,
    EMAIL_VERIFIED,

    // Session
    LOGIN_SUCCEEDED,
    /** Records the real reason in metadata — the reason the client is never told. */
    LOGIN_FAILED,
    ACCOUNT_LOCKED,
    LOGOUT,

    SESSION_REVOKED,
    OTHER_SESSIONS_REVOKED,

    // Tokens
    TOKEN_REFRESHED,
    /** The one that should page someone. A stolen refresh token is being replayed. */
    TOKEN_REUSE_DETECTED,

    // Profile
    /** The user edited their own display fields. Their name is what a colleague identifies them by. */
    PROFILE_UPDATED,

    // Credentials
    PASSWORD_CHANGED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,

    // Federation
    OAUTH_LOGIN_SUCCEEDED,
    OAUTH_ACCOUNT_LINKED;

    @Override
    public String code() {
        return name();
    }
}
