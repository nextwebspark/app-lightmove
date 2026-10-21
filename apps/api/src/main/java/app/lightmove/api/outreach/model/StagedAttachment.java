package app.lightmove.api.outreach.model;

import java.util.UUID;

/**
 * A file written to storage for an email not yet saved: what its row will hold. {@code documentVersionId} is the
 * person's document version it was copied from, or null for an upload.
 */
public record StagedAttachment(String fileName, String contentType, long sizeBytes, String sha256, String storageKey,
                               UUID documentVersionId) {}
