package app.lightmove.api.prompteval.dto;

import app.lightmove.api.prompteval.constant.EvalCaseOutcome;
import java.util.List;
import java.util.UUID;

/**
 * A case the two versions did differently on: the checks that moved, and every scored thing side by side. The
 * answers are to a fictional input.
 */
public record EvalRunCaseComparison(
        UUID caseId,
        String key,
        String name,
        String tag,
        EvalCaseOutcome outcome,
        List<EvalCaseCheckChange> checks,
        List<EvalReadingComparison> readings
) {
}
