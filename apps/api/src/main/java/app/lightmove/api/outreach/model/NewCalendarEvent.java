package app.lightmove.api.outreach.model;

import app.lightmove.api.outreach.constant.MeetingVideo;
import java.time.Instant;

/**
 * A call to put on the consultant's own calendar, with the executive as its one invitee. {@code joinUrl} is a link
 * made elsewhere (Zoom's) that the invite carries in its location and description; null when the calendar makes the
 * video itself, or there is none.
 */
public record NewCalendarEvent(String title, Instant startsAt, Instant endsAt, String inviteeAddress,
                               MeetingVideo video, String joinUrl) {

    public NewCalendarEvent(String title, Instant startsAt, Instant endsAt, String inviteeAddress, MeetingVideo video) {
        this(title, startsAt, endsAt, inviteeAddress, video, null);
    }

    /** The invite's description where it carries a link made elsewhere. */
    public String joinNote() {
        return joinUrl == null ? null : "Join the Zoom meeting: " + joinUrl;
    }
}
