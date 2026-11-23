package app.lightmove.api.prompteval.dto;

import app.lightmove.api.prompteval.constant.EvalRepeatsReason;

/**
 * What a run would take before it is started: {@code calls} is cases × 2 versions × repeats; tokens and minutes
 * are read off the prompt's earlier runs where it has any. {@code tooLarge}: a start would be refused.
 */
public record EvalRunEstimate(
        int baselineVersion,
        int cases,
        int repeats,
        EvalRepeatsReason repeatsReason,
        int calls,
        long tokens,
        int minutes,
        int callCap,
        boolean tooLarge
) {
}
