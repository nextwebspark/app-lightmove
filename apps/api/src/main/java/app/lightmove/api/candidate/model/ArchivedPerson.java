package app.lightmove.api.candidate.model;

import java.util.List;
import java.util.UUID;

/** A person as a whole-workspace export writes them: their notes and the latest file of each document. */
public record ArchivedPerson(UUID personId, String fullName, List<ArchivedNote> notes,
                             List<ArchivedDocumentFile> documents) {
}
