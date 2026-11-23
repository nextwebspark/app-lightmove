package app.lightmove.api.prompteval.dto;

import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * A run to estimate or start: one version of the dataset's prompt against another on one split of its cases.
 * {@code baselineVersion} left out is the version serving now.
 */
public record StartEvalRunRequest(
        @NotNull UUID datasetId,
        @NotNull EvalCaseSplit split,
        @NotNull @Positive Integer candidateVersion,
        @Positive Integer baselineVersion
) {
}
