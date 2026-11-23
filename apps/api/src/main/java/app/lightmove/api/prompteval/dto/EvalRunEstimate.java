package app.lightmove.api.prompteval.dto;

import app.lightmove.api.prompteval.constant.EvalRepeatsReason;

/**
 * What a run would take before it is started: {@code calls} is cases × 2 versions × repeats, and
 * {@code judgeCalls} of them more by the judge, one for each pair of answers, where the dataset has a rubric;
 * tokens and minutes are read off the prompt's earlier runs where it has any. {@code judgedCounts} is whether that
 * rubric's criteria count now, null with no rubric. {@code tooLarge}: a start would be refused.
 */
public record EvalRunEstimate(
        int baselineVersion,
        int cases,
        int repeats,
        EvalRepeatsReason repeatsReason,
        int calls,
        int judgeCalls,
        Boolean judgedCounts,
        long tokens,
        int minutes,
        int callCap,
        boolean tooLarge
) {
}
