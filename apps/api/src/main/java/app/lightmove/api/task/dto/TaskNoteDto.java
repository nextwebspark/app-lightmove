package app.lightmove.api.task.dto;

import app.lightmove.api.candidate.model.LinkedNote;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** The note a task or a bell row came from: its kind and date, never its words. Null once the note is deleted. */
public record TaskNoteDto(UUID id, String kind, Instant writtenAt) {

    public static TaskNoteDto of(UUID noteId, Map<UUID, LinkedNote> known) {
        LinkedNote note = noteId == null ? null : known.get(noteId);
        return note == null ? null : new TaskNoteDto(note.id(), note.kind().value(), note.writtenAt());
    }
}
