package app.lightmove.api.outreach.model;

import app.lightmove.api.outreach.constant.MeetingVideo;
import java.time.Instant;

/** A call to put on the consultant's own calendar, with the executive as its one invitee. */
public record NewCalendarEvent(String title, Instant startsAt, Instant endsAt, String inviteeAddress,
                               MeetingVideo video) {}
