package app.lightmove.api.outreach.constant;

/** The video link a booked call carries, created by the consultant's own calendar. */
public enum MeetingVideo {
    GOOGLE_MEET,
    MICROSOFT_TEAMS,
    /** Made on the consultant's own Zoom account, its link put on the invite by whichever calendar sends it. */
    ZOOM,
    NONE
}
