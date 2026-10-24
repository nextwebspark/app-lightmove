package app.lightmove.api.outreach.constant;

/** Where a consultant's working hours were read from, first that applies. */
public enum WorkingHoursSource {
    /** Outlook's own, stated by a direct Microsoft mailbox's calendar. */
    CALENDAR,
    /** The consultant's own, set in Settings → Availability. */
    PERSONAL,
    /** The workspace's, chosen by an admin or, until one does, the week the firm's country keeps. */
    WORKSPACE
}
