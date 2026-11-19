package app.lightmove.api.candidate.model;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** A calendar meeting a person's meeting notes are about, as the note snapshots it. */
@NamedInterface(name = "api", propagate = false)
public record LoggedMeeting(UUID meetingId, Instant startsAt, String title) {}
