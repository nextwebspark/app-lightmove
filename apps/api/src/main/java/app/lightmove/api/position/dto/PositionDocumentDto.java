package app.lightmove.api.position.dto;

import java.time.Instant;

/**
 * The stored position description. Metadata only — the bytes are fetched by their own endpoint, so a
 * page load never drags the file through the connection pool.
 */
public record PositionDocumentDto(String fileName, String contentType, long fileSize, Instant uploadedAt) {}
