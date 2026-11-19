package app.lightmove.api.candidate.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * What a restore needs to know of a deleted note before bringing it back: whose it is, what it is about, and its
 * words, which a door may have filed again meanwhile.
 */
public interface DeletedPersonNoteRow {

    UUID getAuthorUserId();

    UUID getProjectId();

    String getBody();

    /** A {@code NoteOutcome} name, or null. */
    String getOutcome();

    /** A {@code PersonNoteKind} name. */
    String getKind();

    /** A {@code NoteFormat} name. */
    String getBodyFormat();

    Instant getDeletedAt();

    /** A door files a plain general note with no outcome; only such a note can have been filed again by one. */
    default boolean isFiledByADoor() {
        return getOutcome() == null && "GENERAL".equals(getKind()) && "PLAIN".equals(getBodyFormat());
    }
}
