package app.lightmove.api.core.llm.model;

import app.lightmove.api.core.llm.constant.AiProvider;

/** What one version of a prompt did on one model over a span of days: counts and token totals, never content. */
public record PromptModelUsage(
        int version,
        AiProvider provider,
        String modelId,
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
