package app.lightmove.api.core.llm.model;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.FinishKind;

/** One answered call, as it is added to its version's row of the day. Never any content. */
public record PromptCallCount(
        String promptId,
        int version,
        AiProvider provider,
        String modelId,
        long inputTokens,
        long outputTokens,
        FinishKind finish
) {
}
