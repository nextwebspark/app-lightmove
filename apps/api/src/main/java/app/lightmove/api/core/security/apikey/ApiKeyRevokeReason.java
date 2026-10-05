package app.lightmove.api.core.security.apikey;

/** Why a key stopped working before it expired. */
public enum ApiKeyRevokeReason {

    /** Someone pressed Revoke: the owner, or an admin. */
    REVOKED,

    /** Its owner's membership ended, so a personal key has nobody's access left to borrow. */
    MEMBER_REMOVED,

    WORKSPACE_DELETED
}
