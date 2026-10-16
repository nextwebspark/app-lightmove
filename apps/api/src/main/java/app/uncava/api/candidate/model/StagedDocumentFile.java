package app.uncava.api.candidate.model;

import app.uncava.api.core.storage.constant.DocumentFormat;

/** An upload already checked, hashed and written to storage, waiting for its rows. */
public record StagedDocumentFile(String fileName, DocumentFormat format, long sizeBytes, String sha256,
                                 String storageKey) {}
