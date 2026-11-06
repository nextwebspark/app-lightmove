package app.lightmove.api.candidate.model;

import java.time.Instant;
import java.util.UUID;

/**
 * When a logged call, LinkedIn message or meeting happened, its optional title, and the calendar meeting or the
 * Notetaker recording it is about — at most one of the two.
 */
public record NoteOccurrence(Instant occurredAt, String title, UUID meetingId, UUID recordingId) {

    public static final NoteOccurrence NONE = new NoteOccurrence(null, null, null, null);
}
