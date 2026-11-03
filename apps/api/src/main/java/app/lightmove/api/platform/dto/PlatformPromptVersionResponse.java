package app.lightmove.api.platform.dto;

import java.time.Instant;

/** A saved version, without its body: Restore copies the body server-side. */
public record PlatformPromptVersionResponse(int version, boolean active, String createdByName, Instant createdAt) {}
