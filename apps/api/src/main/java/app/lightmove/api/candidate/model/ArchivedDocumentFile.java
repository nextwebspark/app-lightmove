package app.lightmove.api.candidate.model;

/** The latest version of one of a person's documents, and where its bytes are. */
public record ArchivedDocumentFile(String category, String title, int versionNo, StoredDocumentFile file) {
}
