package app.lightmove.api.prompteval.model;

import app.lightmove.api.prompteval.constant.RubricState;
import java.util.List;

/**
 * What the judge found across a finished run, and whether it counted, as things stood when the run finished:
 * labelling pairs later changes no run. {@code agreementPercent} is null where no check had measured it.
 */
public record EvalRunJudged(
        int rubricVersion,
        Integer judgeVersion,
        String judgeModel,
        RubricState state,
        Double agreementPercent,
        boolean counted,
        double tolerancePoints,
        int pairs,
        List<JudgedCriterionScore> criteria
) {
}
