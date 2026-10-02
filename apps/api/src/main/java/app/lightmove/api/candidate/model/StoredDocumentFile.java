package app.lightmove.api.candidate.model;

/** One version's file as a download needs it: what to call it, what it is, and where its bytes are. */
public record StoredDocumentFile(String fileName, String contentType, long sizeBytes, String storageKey,
                                 boolean previewable) {}
