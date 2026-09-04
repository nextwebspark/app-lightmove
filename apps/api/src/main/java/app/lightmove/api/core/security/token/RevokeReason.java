package app.lightmove.api.core.security.token;

/** Why a refresh token stopped being usable — and, from that, whether replaying it is an attack. */
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
    ADMIN_REVOKED,

    /**
     * The owner ended this session from another device. Distinct from {@link #ADMIN_REVOKED} so the
     * trail says whether the account holder acted or LightMove staff did.
     */
    USER_REVOKED,

    /**
     * Replaced by a newer session for the same client — pairing the browser extension again ends the
     * one the account already held. An expected terminal state, so it is not theft on replay: the
     * extension that missed the re-pair is a stale device catching up.
     */
    SUPERSEDED;

    /**
     * Whether presenting a token revoked for this reason is evidence of theft rather than an ordinary
     * dead session.
     *
     * <p>{@link #ROTATED} is the load-bearing case and must stay here: a rotated-away token being
     * presented again <i>is</i> the attack signature, because the legitimate client has already moved
     * on to its successor. Do not widen this into "any revoked token is fine".
     *
     * <p>The rest are expected terminal states. Treating them as theft told a user who had simply
     * signed out that their session ended "for security reasons", and fired TOKEN_REUSE_DETECTED — the
     * one audit event meant to page a human — on every routine logout and password reset, which made
     * the alert useless.
     */
    public boolean indicatesTheftOnReplay() {
        return this == ROTATED || this == REUSE_DETECTED;
    }
}
