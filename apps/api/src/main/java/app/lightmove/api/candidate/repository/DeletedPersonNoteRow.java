package app.lightmove.api.candidate.repository;

import java.util.UUID;

/** What a restore needs to know of a deleted note before bringing it back: whose it is and what it is about. */
public interface DeletedPersonNoteRow {

    UUID getAuthorUserId();

    UUID getProjectId();
}
