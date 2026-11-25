package app.lightmove.api.prompteval.dto;

import app.lightmove.api.prompteval.constant.EvalCaseOutcome;
import app.lightmove.api.prompteval.constant.JudgePick;
import java.util.List;
import java.util.UUID;

/**
 * A case the two versions did differently on: the checks that moved, and every scored thing side by side, or
 * {@code prose} for a prompt answered in words. {@code judged} is the judge on each criterion and
 * {@code candidateShownAs} the order it saw each pair in, by repeat; both empty for a dataset with no rubric. The
 * answers are to a fictional input.
 */
public record EvalRunCaseComparison(
        UUID caseId,
        String key,
        String name,
        String tag,
        EvalCaseOutcome outcome,
        List<EvalCaseCheckChange> checks,
        List<EvalReadingComparison> readings,
        EvalCaseProse prose,
        List<EvalCaseJudgedCriterion> judged,
        List<JudgePick> candidateShownAs
) {
}
