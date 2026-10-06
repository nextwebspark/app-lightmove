package app.lightmove.api.core.security.oauth;

/** Why a grant ended, in the audit trail. {@link #REFRESH_REUSE} is the theft signature, every other one an ordinary end. */
public enum OAuthGrantRevokeReason {
    REVOKED,
    /** The app revoked one of its own tokens (RFC 7009), as it does when someone disconnects it from its side. */
    CLIENT_REVOKED,
    MEMBER_REMOVED,
    WORKSPACE_DELETED,
    PASSWORD_CHANGED,
    REFRESH_REUSE
}
