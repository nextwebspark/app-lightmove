package app.lightmove.api.outreach.constant;

/** Why someone chosen for a sequence cannot be put on it. The dialog shows it; Start refuses it. */
public enum OutreachSkipReason {

    NO_EMAIL,
    DO_NOT_CONTACT,

    /** Processing of the person is restricted: nobody approaches them until it is lifted. */
    PROCESSING_RESTRICTED,

    /** At a status out of process on this position — not interested, off-limits, screened out and the like. */
    LEFT_THE_RUNNING,

    ALREADY_IN_SEQUENCE
}
