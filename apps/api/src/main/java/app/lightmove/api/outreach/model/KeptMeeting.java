package app.lightmove.api.outreach.model;

import java.time.Instant;
import java.util.UUID;

/** A meeting with a person as another feature reads it: the call, and the link to join it. */
public record KeptMeeting(UUID id, UUID personId, String title, Instant startsAt, Instant endsAt, String joinUrl) {}
