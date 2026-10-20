package app.lightmove.api.candidate.repository;

import java.util.UUID;

/**
 * What a restore needs to know of a deleted note before bringing it back: whose it is, what it is about, and its
 * words, which a door may have filed again meanwhile.
 */
public interface DeletedPersonNoteRow {

    UUID getAuthorUserId();

    UUID getProjectId();

    String getBody();
}
