package app.lightmove.api.promptregistry.dto;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.PromptOrigin;
import app.lightmove.api.core.llm.model.GenerationSettings;
import java.time.Instant;

/** One version in a prompt's history, without its text. {@code author} null: a release shipped it. */
public record PromptVersionSummary(
        int version,
        PromptOrigin origin,
        boolean live,
        String changeNote,
        Instant createdAt,
        String author,
        AiProvider provider,
        String modelId,
        String modelName,
        GenerationSettings settings
) {
}
