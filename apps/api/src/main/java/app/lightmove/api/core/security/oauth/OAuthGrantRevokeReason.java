package app.lightmove.api.core.security.oauth;

/** Why a grant ended, in the audit trail. {@link #REFRESH_REUSE} is the theft signature, every other one an ordinary end. */
public enum OAuthGrantRevokeReason {
    REVOKED,
    MEMBER_REMOVED,
    WORKSPACE_DELETED,
    PASSWORD_CHANGED,
    REFRESH_REUSE
}
