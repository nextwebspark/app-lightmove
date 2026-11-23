package app.lightmove.api.prompteval.dto;

import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import app.lightmove.api.prompteval.constant.EvalRunFailure;
import app.lightmove.api.prompteval.constant.EvalRunStatus;
import app.lightmove.api.prompteval.constant.EvalRunVerdict;
import java.time.Instant;
import java.util.UUID;

/**
 * A run as the list shows it. {@code verdict} is null until it finishes and {@code failure} unless it failed;
 * {@code callsMade} counts a repair's second ask, so it can pass {@code callsPlanned}.
 */
public record EvalRunOverview(
        UUID id,
        long number,
        String promptId,
        String promptName,
        UUID datasetId,
        String datasetName,
        EvalCaseSplit split,
        int candidateVersion,
        int baselineVersion,
        EvalRunStatus status,
        EvalRunVerdict verdict,
        EvalRunFailure failure,
        int cases,
        int repeats,
        int callsPlanned,
        int callsMade,
        long inputTokens,
        long outputTokens,
        String startedBy,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
}
