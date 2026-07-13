package app.lightmove.api.common.audit;

/**
 * Every security-relevant thing that can happen, as a closed set.
 *
 * <p>An enum rather than a free-form string so that a typo cannot silently create a new event type
 * that no alert or report is watching for.
 */
public enum AuditEventType {

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

    // Tokens
    TOKEN_REFRESHED,
    /** The one that should page someone. A stolen refresh token is being replayed. */
    TOKEN_REUSE_DETECTED,
    TOKEN_FAMILY_REVOKED,

    // Credentials
    PASSWORD_CHANGED,
    PASSWORD_RESET_REQUESTED,

    // Federation
    OAUTH_LOGIN_SUCCEEDED,
    OAUTH_ACCOUNT_LINKED,

    // Tenancy
    WORKSPACE_CREATED,
    MEMBER_INVITED,
    INVITATION_ACCEPTED,

    /** Someone found a workspace on their email domain and asked to be let in. */
    JOIN_REQUESTED,
    /** An admin let them in — the moment a person gains access to a firm's candidate data. */
    JOIN_APPROVED,
    JOIN_REJECTED,

    MEMBER_ROLE_CHANGED,
    MEMBER_REMOVED,

    // Abuse
    RATE_LIMIT_EXCEEDED
}
