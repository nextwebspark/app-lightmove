package app.lightmove.api.prompteval.dto;

import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import app.lightmove.api.prompteval.constant.EvalRunFailure;
import app.lightmove.api.prompteval.constant.EvalRunStatus;
import app.lightmove.api.prompteval.constant.EvalRunVerdict;
import java.time.Instant;
import java.util.UUID;

/**
 * A run as the list shows it. {@code verdict} is null until it finishes, and for a finished run that gave no
 * result; {@code failure} is null unless it failed; {@code judged}: its dataset has a rubric, so a judge compares
 * each pair of answers and its calls are among the run's;
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
        boolean judged,
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
