package app.lightmove.api.core.llm.model;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.FinishKind;

/**
 * What one event adds to its version's row of the day: an answered call, or one of the ways a call ends without a
 * usable answer. {@code finish} is null unless a call was answered. Never any content.
 */
public record PromptCallCount(
        String promptId,
        int version,
        AiProvider provider,
        String modelId,
        int calls,
        long inputTokens,
        long outputTokens,
        FinishKind finish,
        int guardRefusals,
        int schemaReasks,
        int schemaFailures
) {

    /** What is recorded when neither the answer nor the request names a model. */
    private static final String CONFIGURED_MODEL = "configured";

    public static PromptCallCount answered(PromptCallTrace trace, long inputTokens, long outputTokens,
                                           FinishKind finish, boolean reask) {
        return of(trace, 1, inputTokens, outputTokens, finish, 0, reask ? 1 : 0, 0);
    }

    public static PromptCallCount guardRefusal(PromptCallTrace trace) {
        return of(trace, 0, 0, 0, null, 1, 0, 0);
    }

    public static PromptCallCount schemaFailure(PromptCallTrace trace) {
        return of(trace, 0, 0, 0, null, 0, 0, 1);
    }

    private static PromptCallCount of(PromptCallTrace trace, int calls, long inputTokens, long outputTokens,
                                      FinishKind finish, int guardRefusals, int schemaReasks, int schemaFailures) {
        return new PromptCallCount(trace.version().promptId(), trace.version().version(), trace.provider(),
                trace.model() == null ? CONFIGURED_MODEL : trace.model(), calls, inputTokens, outputTokens, finish,
                guardRefusals, schemaReasks, schemaFailures);
    }
}
