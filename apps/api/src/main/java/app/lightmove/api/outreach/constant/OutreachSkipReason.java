package app.lightmove.api.outreach.constant;

/** Why someone chosen for a sequence cannot be put on it. The dialog shows it; Start refuses it. */
public enum OutreachSkipReason {

    NO_EMAIL,
    DO_NOT_CONTACT,

    /** Not interested, off-limits or out of scope on this position. */
    LEFT_THE_RUNNING,

    ALREADY_IN_SEQUENCE
}
