package app.lightmove.api.prompteval.model;

import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import app.lightmove.api.prompteval.constant.EvalRunFailure;
import app.lightmove.api.prompteval.constant.EvalRunStatus;
import app.lightmove.api.prompteval.constant.EvalRunVerdict;
import java.time.Instant;
import java.util.UUID;

/**
 * One run as stored. {@code candidateModel} and {@code baselineModel} are what answered, null until one has;
 * {@code summary} is null until the run finishes, and {@code verdict} until then and for a finished run that gave
 * no result; {@code rubricId} is null for a dataset with no rubric; {@code startedBy} is null once the account is
 * gone.
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
        UUID rubricId,
        Integer judgeVersion,
        String judgeModel,
        UUID startedBy,
        String startedByName,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
}
