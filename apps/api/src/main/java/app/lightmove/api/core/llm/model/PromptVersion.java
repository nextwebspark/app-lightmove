package app.lightmove.api.core.llm.model;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.PromptOrigin;
import java.time.Instant;

/**
 * One version of a prompt without its text. {@code provider} and {@code modelId} are null when the configured model
 * answers; {@code authorName} is null for a version a release shipped, or one whose author's account is gone.
 */
public record PromptVersion(
        String promptId,
        int version,
        PromptOrigin origin,
        AiProvider provider,
        String modelId,
        GenerationSettings settings,
        String changeNote,
        Instant createdAt,
        String authorName
) {
}
