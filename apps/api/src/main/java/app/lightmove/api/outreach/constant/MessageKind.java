package app.lightmove.api.outreach.constant;

/** What a conversation line is. Only sequence steps are written so far; the rest arrive with later phases. */
public enum MessageKind {
    /** One step of a sequence run. */
    SEQUENCE_STEP,

    /** Written and sent by a consultant from the conversation. */
    MANUAL,

    /** The executive's answer, read from the consultant's mailbox. */
    REPLY,

    /** A mail server's report that an email never arrived. */
    BOUNCE
}
