package app.lightmove.api.candidate.model;

import org.springframework.modulith.NamedInterface;

/** One version's file as a download needs it: what to call it, what it is, and where its bytes are. */
@NamedInterface(name = "api", propagate = false)
public record StoredDocumentFile(String fileName, String contentType, long sizeBytes, String storageKey,
                                 boolean previewable) {}
