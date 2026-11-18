package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.PersonNoteKind;
import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** A note something points back at, named by its kind and when it was written — never by what it says. */
@NamedInterface(name = "api", propagate = false)
public record LinkedNote(UUID id, PersonNoteKind kind, Instant writtenAt, UUID aboutProjectId) {

    public boolean isReadableFrom(NoteViewpoint viewpoint) {
        return viewpoint.reads(aboutProjectId);
    }
}
