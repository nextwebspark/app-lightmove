package app.lightmove.api.auth.domain;

public enum UserStatus {

    /** Signed up, email not yet proven. Can still sign in unless {@code require-verified-email} is on. */
    PENDING_VERIFICATION,

    ACTIVE,

    /** Blocked by an administrator. Distinct from a temporary lockout, which is time-based and self-healing. */
    SUSPENDED,

    /** Soft-deleted. The row survives because audit events reference it. */
    DELETED;

    /** Whether a user in this state may hold a session at all, verification aside. */
    public boolean canAuthenticate() {
        return this == ACTIVE || this == PENDING_VERIFICATION;
    }
}
