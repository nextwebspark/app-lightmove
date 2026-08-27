package app.lightmove.api.position.dto;

import java.time.Instant;

/**
 * Whether anyone has declared the brief ready, and who. Both fields are null until they have — and
 * publishing is a stamp, not a lock, so a published brief keeps accepting every write.
 */
public record PublicationDto(Instant publishedAt, String publishedBy) {}
