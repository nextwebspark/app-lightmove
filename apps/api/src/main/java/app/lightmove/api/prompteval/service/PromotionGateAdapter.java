package app.lightmove.api.prompteval.service;

import app.lightmove.api.prompteval.constant.EvalRunVerdict;
import app.lightmove.api.prompteval.model.EvalCheckScore;
import app.lightmove.api.prompteval.model.EvalRun;
import app.lightmove.api.prompteval.model.EvalRunJudged;
import app.lightmove.api.prompteval.model.EvalRunSummary;
import app.lightmove.api.prompteval.model.JudgedCriterionScore;
import app.lightmove.api.promptregistry.constant.PromotionGateState;
import app.lightmove.api.promptregistry.model.PromotionGateFailedCheck;
import app.lightmove.api.promptregistry.model.PromotionGateJudged;
import app.lightmove.api.promptregistry.model.PromotionGateResult;
import app.lightmove.api.promptregistry.service.PromotionGate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Answers the registry's question about a put live from the runs: a version's latest finished run on the held-back
 * cases against the version serving. A run started while its prompt's cases still waited for a person's review is
 * not counted, so such a prompt keeps asking for a written reason. A run that finished with no result is named, and
 * asks for one too.
 */
@Component
@RequiredArgsConstructor
class PromotionGateAdapter implements PromotionGate {

    private final EvalRunStore store;

    @Override
    public Map<Integer, PromotionGateResult> against(String promptId, int liveVersion) {
        Map<Integer, PromotionGateResult> byVersion = new LinkedHashMap<>();
        store.latestCountingRuns(promptId, liveVersion)
                .forEach((version, run) -> byVersion.put(version, resultOf(run)));
        return byVersion;
    }

    private static PromotionGateResult resultOf(EvalRun run) {
        EvalRunSummary summary = run.summary();
        return new PromotionGateResult(
                run.verdict() == null ? PromotionGateState.NO_RESULT
                        : run.verdict() == EvalRunVerdict.PASS ? PromotionGateState.PASSED
                        : PromotionGateState.FAILED,
                run.id(), run.number(), run.finishedAt(), run.caseCount(), run.repeats(),
                countOf(summary, 1), countOf(summary, 0), countOf(summary, -1), summary.regressedCases(),
                summary.checks().stream()
                        .filter(EvalCheckScore::worseBeyondTolerance)
                        .map(check -> new PromotionGateFailedCheck(check.label(), check.candidate(),
                                check.baseline()))
                        .toList(),
                run.datasetId(), judgedOf(summary.judged()));
    }

    private static PromotionGateJudged judgedOf(EvalRunJudged judged) {
        if (judged == null) {
            return null;
        }
        return new PromotionGateJudged(judged.counted(), judged.state().name(), judged.criteria().size(),
                (int) judged.criteria().stream()
                        .filter(criterion -> criterion.net() != null && criterion.net() > judged.tolerancePoints())
                        .count(),
                judged.criteria().stream()
                        .filter(JudgedCriterionScore::worseBeyondTolerance)
                        .map(JudgedCriterionScore::name)
                        .toList());
    }

    /** How many checks moved in that direction; one with nothing to compare counts as unmoved. */
    private static int countOf(EvalRunSummary summary, int direction) {
        return (int) summary.checks().stream()
                .filter(check -> (int) Math.signum(check.change() == null ? 0 : check.change()) == direction)
                .count();
    }
}
