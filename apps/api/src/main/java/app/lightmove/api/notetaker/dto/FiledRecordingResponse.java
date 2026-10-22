package app.lightmove.api.notetaker.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A call of this executive whose note this position may read, found by the note it filed rather than by its meeting
 * row, which a calendar read may delete. {@code recordedUntil}: where a partial note stops.
 */
public record FiledRecordingResponse(UUID recordingId, UUID noteId, Instant recordedUntil) {}
