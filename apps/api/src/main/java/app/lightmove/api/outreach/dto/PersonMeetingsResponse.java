package app.lightmove.api.outreach.dto;

import java.util.List;

/** The drawer's Meetings section: what is to come, soonest first, and the latest of what is past. */
public record PersonMeetingsResponse(List<MeetingResponse> upcoming, List<MeetingResponse> past) {}
