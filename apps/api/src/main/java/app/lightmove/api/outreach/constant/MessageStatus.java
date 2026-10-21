package app.lightmove.api.outreach.constant;

/** Where a conversation line stands. A sequence step is written once it went, so it is always {@code SENT}. */
public enum MessageStatus {
    SCHEDULED,
    SENT,
    FAILED,
    CANCELLED,
    RECEIVED
}
