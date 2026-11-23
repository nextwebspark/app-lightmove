package app.lightmove.api.outreach.dto;

import java.util.List;

/** Home's Today: the caller's meetings with executives, soonest first. */
public record MyMeetingsResponse(List<MyMeetingResponse> meetings) {}
