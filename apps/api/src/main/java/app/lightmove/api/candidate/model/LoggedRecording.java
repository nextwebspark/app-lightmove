package app.lightmove.api.candidate.model;

import java.time.Instant;
import java.util.UUID;

/** A Notetaker recording a meeting's notes are about, as the note snapshots it. */
public record LoggedRecording(UUID recordingId, Instant startsAt, String title) {}
