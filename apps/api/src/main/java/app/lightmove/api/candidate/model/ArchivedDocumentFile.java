package app.lightmove.api.candidate.model;

import org.springframework.modulith.NamedInterface;

/** The latest version of one of a person's documents, and where its bytes are. */
@NamedInterface(name = "api", propagate = false)
public record ArchivedDocumentFile(String category, String title, int versionNo, StoredDocumentFile file) {
}
