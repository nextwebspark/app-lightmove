package app.lightmove.api.promptregistry.dto;

import app.lightmove.api.promptregistry.constant.PromotionGateState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The run a put live would rest on, and what it found. Everything but {@code state} is null, and
 * {@code failedChecks} empty, when there is no such run. {@code judged} is null for a run no judge scored.
 */
public record PromotionGateResult(
        PromotionGateState state,
        UUID runId,
        Long runNumber,
        Instant finishedAt,
        Integer cases,
        Integer repeats,
        Integer checksBetter,
        Integer checksSame,
        Integer checksWorse,
        Integer regressedCases,
        List<PromotionGateFailedCheck> failedChecks,
        UUID datasetId,
        PromotionGateJudged judged
) {

    public static final PromotionGateResult NONE = new PromotionGateResult(PromotionGateState.NONE, null, null, null,
            null, null, null, null, null, null, List.of(), null, null);
}
