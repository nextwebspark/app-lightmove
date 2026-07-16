package app.lightmove.api.workspace.constant;

public enum InvitationStatus {
    PENDING,
    ACCEPTED,

    /** Withdrawn by an admin before it was used. */
    REVOKED,

    /** Ran out of time. Set lazily, on the read that notices — not by a background sweep. */
    EXPIRED
}
