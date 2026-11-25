package app.lightmove.api.prompteval.dto;

import app.lightmove.api.prompteval.model.RubricCriterion;
import java.util.List;
import java.util.UUID;

/** The pairs of a dataset's rubric, in order, with its criteria. Empty until labelling has been started. */
public record EvalCalibrationPairs(
        UUID datasetId,
        String datasetName,
        int rubricVersion,
        List<RubricCriterion> criteria,
        int pairsNeeded,
        int thresholdPercent,
        List<EvalCalibrationPair> pairs
) {
}
