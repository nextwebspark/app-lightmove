package app.lightmove.api.prompteval.dto;

import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import app.lightmove.api.prompteval.constant.JudgePick;
import java.util.Map;
import java.util.UUID;

/**
 * One pair to label: the fictional case, its two answers, and the person's picks so far by criterion key. Neither
 * the versions nor the judge's picks are here.
 */
public record EvalCalibrationPair(
        UUID id,
        int position,
        String caseKey,
        String caseName,
        EvalCaseSplit split,
        String input,
        EvalCalibrationAnswer a,
        EvalCalibrationAnswer b,
        Map<String, JudgePick> picks
) {
}
