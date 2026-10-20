package app.lightmove.api.triagecompany.repository;

import java.util.UUID;

/** What a restore needs of a deleted company note: whose it is, and its words, which a door may have filed again. */
public interface DeletedCompanyNoteRow {

    UUID getAuthorUserId();

    String getBody();
}
