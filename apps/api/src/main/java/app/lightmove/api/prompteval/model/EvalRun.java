package app.lightmove.api.prompteval.model;

import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import app.lightmove.api.prompteval.constant.EvalRunFailure;
import app.lightmove.api.prompteval.constant.EvalRunStatus;
import app.lightmove.api.prompteval.constant.EvalRunVerdict;
import java.time.Instant;
import java.util.UUID;

/**
 * One run as stored. {@code candidateModel} and {@code baselineModel} are what answered, null until one has;
 * {@code summary} and {@code verdict} are null until the run finishes; {@code startedBy} is null once the account
 * is gone.
 */
public record EvalRun(
        UUID id,
        long number,
        String promptId,
        UUID datasetId,
        String datasetName,
        EvalCaseSplit split,
        int candidateVersion,
        int baselineVersion,
        String candidateModel,
        String baselineModel,
        int repeats,
        EvalRunStatus status,
        EvalRunVerdict verdict,
        EvalRunFailure failure,
        boolean gateReady,
        int caseCount,
        int callsPlanned,
        int callsMade,
        int callCap,
        long inputTokens,
        long outputTokens,
        EvalRunSummary summary,
        UUID startedBy,
        String startedByName,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
}
