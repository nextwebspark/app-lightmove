package app.lightmove.api.outreach.dto;

import java.util.UUID;

/** The call just booked, by the row the drawer lists it under, so a Notetaker can be sent to it at once. */
public record BookedMeetingResponse(UUID meetingId) {}
