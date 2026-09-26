package app.lightmove.api.core.security.token;

/** Why a refresh token stopped being usable — and, from that, whether replaying it is an attack. */
public enum RevokeReason {

    /** Superseded during a normal refresh. */
    ROTATED,

    LOGOUT,

    /** A rotated token replayed: client or thief is indistinguishable, so the whole family dies. */
    REUSE_DETECTED,

    PASSWORD_CHANGED,
    ADMIN_REVOKED,

    /** The owner ended it from another device; distinct from {@link #ADMIN_REVOKED} for the trail. */
    USER_REVOKED,

    /** Replaced by re-pairing the extension; not theft on replay, just a stale device catching up. */
    SUPERSEDED;

    /**
     * {@link #ROTATED} must stay: a rotated-away token replayed <i>is</i> the attack signature. The rest
     * are ordinary ends; calling them theft fired TOKEN_REUSE_DETECTED on every logout, making it useless.
     */
    public boolean indicatesTheftOnReplay() {
        return this == ROTATED || this == REUSE_DETECTED;
    }
}
