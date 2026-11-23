package app.lightmove.api.promptregistry.dto;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.PromptOrigin;
import app.lightmove.api.core.llm.model.GenerationSettings;
import app.lightmove.api.promptregistry.model.PromotionGateResult;
import java.time.Instant;

/**
 * One version in a prompt's history, without its text. {@code author} null: a release shipped it. {@code gate} is
 * what the evals say about putting it live in place of the one serving, and null for the one serving.
 */
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
        GenerationSettings settings,
        PromotionGateResult gate
) {
}
