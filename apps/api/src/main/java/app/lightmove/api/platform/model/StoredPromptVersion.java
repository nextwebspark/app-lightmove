package app.lightmove.api.platform.model;

import java.time.Instant;

/** A saved version of a prompt, without its body. */
public record StoredPromptVersion(String promptId, int version, boolean active, String createdByName,
                                  Instant createdAt) {}
