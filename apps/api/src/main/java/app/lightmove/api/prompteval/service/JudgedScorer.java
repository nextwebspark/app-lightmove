package app.lightmove.api.prompteval.service;

import app.lightmove.api.prompteval.constant.EvalCaseOutcome;
import app.lightmove.api.prompteval.constant.JudgePick;
import app.lightmove.api.prompteval.constant.JudgedWinner;
import app.lightmove.api.prompteval.dto.EvalCaseJudgedCriterion;
import app.lightmove.api.prompteval.model.EvalJudgement;
import app.lightmove.api.prompteval.model.EvalRubric;
import app.lightmove.api.prompteval.model.EvalRun;
import app.lightmove.api.prompteval.model.EvalRunJudged;
import app.lightmove.api.prompteval.model.JudgedCriterionScore;
import app.lightmove.api.prompteval.model.RubricCriterion;
import app.lightmove.api.prompteval.model.RubricStanding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Turns the judge's stored picks into what a run found on each criterion. Win, loss and tie are counted in pairs,
 * never as a score: the judge compares, it does not grade.
 */
final class JudgedScorer {

    /** How many points a criterion's net may fall against the candidate before the candidate counts as worse. */
    static final double TOLERANCE_POINTS = 10;

    private static final double PERCENT = 100;
    private static final double EPSILON = 1e-9;

    private JudgedScorer() {
    }

    /** {@code standing} is the rubric's as the run finishes: whether its criteria count is fixed with the run. */
    static EvalRunJudged judgedOf(EvalRubric rubric, EvalRun run, RubricStanding standing,
                                  List<EvalJudgement> judgements) {
        Set<String> pairs = new HashSet<>();
        judgements.forEach(judgement -> pairs.add(judgement.caseId() + ":" + judgement.repeat()));
        List<JudgedCriterionScore> scores = new ArrayList<>();
        for (RubricCriterion criterion : rubric.criteria()) {
            List<EvalJudgement> ofCriterion = judgements.stream()
                    .filter(judgement -> judgement.criterionKey().equals(criterion.key()))
                    .toList();
            int candidate = wonBy(ofCriterion, JudgedWinner.CANDIDATE);
            int baseline = wonBy(ofCriterion, JudgedWinner.BASELINE);
            int ties = wonBy(ofCriterion, JudgedWinner.TIE);
            int judged = candidate + baseline + ties;
            Double net = judged == 0 ? null : PERCENT * (candidate - baseline) / judged;
            scores.add(new JudgedCriterionScore(criterion.key(), criterion.name(), candidate, baseline, ties,
                    pairs.size() - judged, rounded(net), net != null && -net > TOLERANCE_POINTS + EPSILON));
        }
        return new EvalRunJudged(rubric.version(), run.judgeVersion(), run.judgeModel(), standing.state(),
                standing.agreementPercent(), standing.counts(), TOLERANCE_POINTS, pairs.size(), scores);
    }

    /**
     * One case's pairs on each criterion: the side most pairs went to, how many, and the judge's reason on the
     * first. Sides level on pairs are a tie.
     */
    static List<EvalCaseJudgedCriterion> ofCase(EvalRubric rubric, List<EvalJudgement> ofCase) {
        List<EvalCaseJudgedCriterion> judged = new ArrayList<>();
        for (RubricCriterion criterion : rubric.criteria()) {
            List<EvalJudgement> picks = ofCase.stream()
                    .filter(judgement -> judgement.criterionKey().equals(criterion.key()) && judgement.winner() != null)
                    .sorted(Comparator.comparingInt(EvalJudgement::repeat))
                    .toList();
            if (picks.isEmpty()) {
                continue;
            }
            int candidate = wonBy(picks, JudgedWinner.CANDIDATE);
            int baseline = wonBy(picks, JudgedWinner.BASELINE);
            int ties = wonBy(picks, JudgedWinner.TIE);
            JudgedWinner result = candidate > baseline && candidate > ties ? JudgedWinner.CANDIDATE
                    : baseline > candidate && baseline > ties ? JudgedWinner.BASELINE : JudgedWinner.TIE;
            int went = result == JudgedWinner.CANDIDATE ? candidate : result == JudgedWinner.BASELINE ? baseline
                    : ties;
            judged.add(new EvalCaseJudgedCriterion(criterion.key(), criterion.name(), result, went, picks.size(),
                    picks.stream().map(EvalJudgement::reason).filter(Objects::nonNull).findFirst().orElse(null)));
        }
        return judged;
    }

    /** The order the judge saw each of a case's pairs in, by repeat: what the candidate's answer was shown as. */
    static List<JudgePick> orderOf(List<EvalJudgement> ofCase) {
        List<JudgePick> order = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        ofCase.stream().sorted(Comparator.comparingInt(EvalJudgement::repeat)).forEach(judgement -> {
            if (seen.add(judgement.repeat())) {
                order.add(judgement.candidateShownAs());
            }
        });
        return order;
    }

    /** For a prompt only the judge scores: a case the baseline took any criterion of regressed. Null: all ties. */
    static EvalCaseOutcome outcomeOf(List<EvalCaseJudgedCriterion> judged) {
        if (judged.stream().anyMatch(criterion -> criterion.result() == JudgedWinner.BASELINE)) {
            return EvalCaseOutcome.REGRESSION;
        }
        return judged.stream().anyMatch(criterion -> criterion.result() == JudgedWinner.CANDIDATE)
                ? EvalCaseOutcome.BETTER : null;
    }

    static List<EvalJudgement> judgementsOn(UUID caseId, List<EvalJudgement> judgements) {
        return judgements.stream().filter(judgement -> judgement.caseId().equals(caseId)).toList();
    }

    private static int wonBy(List<EvalJudgement> judgements, JudgedWinner winner) {
        return (int) judgements.stream().filter(judgement -> judgement.winner() == winner).count();
    }

    private static Double rounded(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }
}
