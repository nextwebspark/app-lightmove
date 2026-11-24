package app.lightmove.api.promptregistry.dto;

import app.lightmove.api.core.llm.constant.AiProvider;

/** The calls of one version on one model over the days asked for. Counts and token totals only. */
public record PromptModelUsageResponse(
        AiProvider provider,
        String modelId,
        String modelName,
        long calls,
        long inputTokens,
        long outputTokens,
        long finishStop,
        long finishMaxTokens,
        long finishSafety,
        long finishOther,
        long guardRefusals,
        long schemaReasks,
        long schemaFailures
) {
}
