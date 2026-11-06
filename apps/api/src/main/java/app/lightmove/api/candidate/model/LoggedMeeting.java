package app.lightmove.api.candidate.model;

import java.time.Instant;
import java.util.UUID;

/** A calendar meeting a person's meeting notes are about, as the note snapshots it. */
public record LoggedMeeting(UUID meetingId, Instant startsAt, String title) {}
