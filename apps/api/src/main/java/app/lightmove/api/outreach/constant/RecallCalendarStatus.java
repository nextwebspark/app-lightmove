package app.lightmove.api.outreach.constant;

/** Where Recall says a calendar stands. {@code DISCONNECTED} is Recall's refresh being refused: reconnect needed. */
public enum RecallCalendarStatus {
    CONNECTING,
    CONNECTED,
    DISCONNECTED
}
