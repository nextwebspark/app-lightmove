package app.lightmove.api.promptregistry.dto;

import app.lightmove.api.core.llm.constant.FinishKind;
import tools.jackson.databind.JsonNode;

/** One Try it: the answer as the model returned it and as the feature read it, and what the call took. */
public record PromptTrialResponse(
        int version,
        String rawAnswer,
        JsonNode parsedAnswer,
        String model,
        long inputTokens,
        long outputTokens,
        FinishKind finish,
        long tookMillis
) {
}
