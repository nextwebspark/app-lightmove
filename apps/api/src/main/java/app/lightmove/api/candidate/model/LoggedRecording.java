package app.lightmove.api.candidate.model;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** A Notetaker recording a meeting's notes are about, as the note snapshots it. */
@NamedInterface(name = "api", propagate = false)
public record LoggedRecording(UUID recordingId, Instant startsAt, String title) {}
