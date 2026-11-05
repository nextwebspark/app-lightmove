package app.lightmove.api.candidate.model;

import java.time.Instant;
import java.util.UUID;

/** When a logged call, LinkedIn message or meeting happened, its optional title, and the meeting it is about. */
public record NoteOccurrence(Instant occurredAt, String title, UUID meetingId) {

    public static final NoteOccurrence NONE = new NoteOccurrence(null, null, null);
}
