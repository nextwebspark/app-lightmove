package app.uncava.api.task.dto;

import app.uncava.api.candidate.model.LinkedNote;
import app.uncava.api.candidate.model.NoteViewpoint;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The note a task or a bell row came from: its kind and date, never its words. Null once the note is deleted, and
 * for a note its position does not read.
 */
public record TaskNoteDto(UUID id, String kind, Instant writtenAt) {

    public static TaskNoteDto of(UUID noteId, UUID projectId, Map<UUID, LinkedNote> known) {
        LinkedNote note = noteId == null || projectId == null ? null : known.get(noteId);
        return note == null || !note.isReadableFrom(NoteViewpoint.position(projectId)) ? null
                : new TaskNoteDto(note.id(), note.kind().value(), note.writtenAt());
    }
}
