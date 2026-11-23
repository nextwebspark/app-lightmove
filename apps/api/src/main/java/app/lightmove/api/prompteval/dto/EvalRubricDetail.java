package app.lightmove.api.prompteval.dto;

import app.lightmove.api.prompteval.constant.RubricState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A dataset's rubric, and whether its judged criteria count. {@code agreed} of {@code picks} are the last finished
 * check's, null before one; {@code checkedJudgeVersion} is the judge's version that made them and
 * {@code judgeVersion} the one serving now. {@code checkJudged} is how many pairs a check that is going has judged.
 * {@code pairsAvailable}: pairs are drawn, or there are finished dev runs enough to draw them from.
 */
public record EvalRubricDetail(
        UUID id,
        int version,
        RubricState state,
        int thresholdPercent,
        int pairsNeeded,
        int pairsDrawn,
        int pairsLabelled,
        Integer agreed,
        Integer picks,
        Double agreementPercent,
        List<EvalRubricCriterionDetail> criteria,
        String labelledBy,
        Instant labelledAt,
        Instant checkedAt,
        Integer checkedJudgeVersion,
        Integer judgeVersion,
        String judgeModel,
        Double judgeTemperature,
        Integer checkJudged,
        boolean lastCheckFailed,
        boolean pairsAvailable,
        List<EvalRubricDifference> differences
) {
}
