package app.lightmove.api.outreach.constant;

/** Where one step of a person's run stands, as the drawer shows it. */
public enum OutreachStepState {
    SENT,

    /** The next to go, with a time. */
    SCHEDULED,

    /** Due after the one before it goes. */
    WAITING,

    /** The run ended before it. */
    NOT_SENT
}
