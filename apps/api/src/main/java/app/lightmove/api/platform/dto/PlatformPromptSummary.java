package app.lightmove.api.platform.dto;

import java.time.Instant;
import java.util.List;

/** A prompt on the list: shipped, or which saved version is in use and whose it is. */
public record PlatformPromptSummary(
        String id,
        String label,
        boolean overridden,
        Integer activeVersion,
        List<String> variables,
        String updatedByName,
        Instant updatedAt
) {}
