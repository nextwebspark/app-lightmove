package app.uncava.api.candidate.model;

import app.uncava.api.candidate.constant.PersonNoteKind;
import java.time.Instant;
import java.util.UUID;

/** A note something points back at, named by its kind and when it was written — never by what it says. */
public record LinkedNote(UUID id, PersonNoteKind kind, Instant writtenAt, UUID aboutProjectId) {

    public boolean isReadableFrom(NoteViewpoint viewpoint) {
        return viewpoint.reads(aboutProjectId);
    }
}
