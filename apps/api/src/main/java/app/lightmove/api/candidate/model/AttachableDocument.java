package app.lightmove.api.candidate.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** One version of a person's document, as an email that is to carry it needs it: which it is, and its file. */
@NamedInterface(name = "api", propagate = false)
public record AttachableDocument(UUID documentId, UUID versionId, String sha256, StoredDocumentFile file) {}
