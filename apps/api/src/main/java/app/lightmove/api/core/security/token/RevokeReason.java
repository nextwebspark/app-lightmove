package app.lightmove.api.core.security.token;

public enum RevokeReason {

    /** Superseded by a newer token during a normal refresh. The ordinary end of a token's life. */
    ROTATED,

    LOGOUT,

    /**
     * An already-rotated token was presented again. Either the real client replayed it or an attacker
     * stole it — indistinguishable from here, so the whole family dies and everyone re-authenticates.
     */
    REUSE_DETECTED,

    PASSWORD_CHANGED,
    ADMIN_REVOKED
}
