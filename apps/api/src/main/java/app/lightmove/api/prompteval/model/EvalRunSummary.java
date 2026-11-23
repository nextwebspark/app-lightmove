package app.lightmove.api.prompteval.model;

import java.util.List;

/**
 * What a finished run found, stored with it. {@code tolerancePoints} is how far a check's pass rate could fall
 * before the candidate counted as worse, as it stood when the run was scored. {@code judged} is null for a run on a
 * dataset with no rubric.
 */
public record EvalRunSummary(
        double tolerancePoints,
        List<EvalCheckScore> checks,
        EvalSideStability candidate,
        EvalSideStability baseline,
        int regressedCases,
        int betterCases,
        int unchangedCases,
        EvalRunJudged judged
) {
}
