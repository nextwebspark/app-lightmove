package app.lightmove.api.promptregistry.model;

import app.lightmove.api.promptregistry.constant.PromotionGateState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The run a put live would rest on, and what it found. Everything but {@code state} is null, and
 * {@code failedChecks} empty, when there is no such run.
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
        List<PromotionGateFailedCheck> failedChecks
) {

    public static final PromotionGateResult NONE = new PromotionGateResult(PromotionGateState.NONE, null, null, null,
            null, null, null, null, null, null, List.of());
}
