package app.lightmove.api.candidate.model;

import java.time.Instant;
import java.util.UUID;

/** The recorded call a change came from, as its timeline line names it. */
public record CallReference(UUID recordingId, String title, Instant startsAt) {}
