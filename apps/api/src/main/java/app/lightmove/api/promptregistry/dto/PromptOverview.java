package app.lightmove.api.promptregistry.dto;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.PromptKind;
import app.lightmove.api.core.llm.constant.PromptOrigin;
import java.time.Instant;

/**
 * One row of the prompts list: what serves, on which model, and whether it needs a look. {@code modelId} null is the
 * configured model; {@code modelRetiresInDays} is null unless the live model is named and states a date.
 * {@code modelUnavailable}: the live version names a model that is retired, no longer listed or unable to answer
 * this prompt, so the configured model is answering it instead. {@code health} is null unless the live version
 * replaced another, which it is then compared with.
 */
public record PromptOverview(
        String id,
        String name,
        String description,
        PromptKind kind,
        int liveVersion,
        PromptOrigin origin,
        AiProvider provider,
        String modelId,
        String modelName,
        Integer modelRetiresInDays,
        boolean modelUnavailable,
        boolean shippedChanged,
        Instant lastChangedAt,
        String lastChangedBy,
        PromptLiveHealth health
) {
}
