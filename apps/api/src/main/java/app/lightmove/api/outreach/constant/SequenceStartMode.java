package app.lightmove.api.outreach.constant;

/**
 * When Start sends the first email. {@code NOW} and {@code AT} are the consultant's own choice of time, so
 * the sending window does not move them; {@code NEXT_WINDOW} waits for the sequence's days and hours.
 */
public enum SequenceStartMode {
    NOW,
    NEXT_WINDOW,
    AT
}
