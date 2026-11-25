package app.lightmove.api.prompteval.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.llm.constant.EvalCheckVerdict;
import app.lightmove.api.core.llm.service.EvalCheck;
import app.lightmove.api.core.llm.service.EvalSubject;
import app.lightmove.api.prompteval.constant.EvalCaseOutcome;
import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import app.lightmove.api.prompteval.constant.EvalRunStatus;
import app.lightmove.api.prompteval.constant.EvalRunVerdict;
import app.lightmove.api.prompteval.constant.JudgePick;
import app.lightmove.api.prompteval.constant.JudgedWinner;
import app.lightmove.api.prompteval.constant.RubricState;
import app.lightmove.api.prompteval.dto.EvalCaseJudgedCriterion;
import app.lightmove.api.prompteval.model.EvalJudgement;
import app.lightmove.api.prompteval.model.EvalRubric;
import app.lightmove.api.prompteval.model.EvalRun;
import app.lightmove.api.prompteval.model.EvalRunCase;
import app.lightmove.api.prompteval.dto.EvalRunJudged;
import app.lightmove.api.prompteval.dto.EvalRunSummary;
import app.lightmove.api.prompteval.dto.JudgedCriterionScore;
import app.lightmove.api.prompteval.model.RubricCriterion;
import app.lightmove.api.prompteval.model.RubricStanding;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** #1322: the judge's picks become win, loss and tie counts, and count toward a result only while the rubric does. */
class JudgedScorerTest {

    private static final UUID FIRST = UUID.randomUUID();
    private static final UUID SECOND = UUID.randomUUID();
    private static final EvalRubric RUBRIC = new EvalRubric(UUID.randomUUID(), UUID.randomUUID(), 1, List.of(
            new RubricCriterion("specific", "Specific to the person", "It names something from the profile."),
            new RubricCriterion("plain", "Plain and brief", "One or two plain sentences.")));
    private static final List<EvalRunCase> CASES = List.of(
            new EvalRunCase(FIRST, "OP-1", "First", null, null, null),
            new EvalRunCase(SECOND, "OP-2", "Second", null, null, null));

    @Test
    @DisplayName("a criterion is counted in pairs, and its net is the candidate's share less the baseline's")
    void countsPairsPerCriterion() {
        List<EvalJudgement> judgements = new ArrayList<>();
        judgements.addAll(pair(FIRST, 0, JudgedWinner.CANDIDATE, JudgedWinner.TIE));
        judgements.addAll(pair(FIRST, 1, JudgedWinner.CANDIDATE, JudgedWinner.TIE));
        judgements.addAll(pair(SECOND, 0, JudgedWinner.BASELINE, JudgedWinner.TIE));
        judgements.addAll(pair(SECOND, 1, JudgedWinner.TIE, null));

        EvalRunJudged judged = JudgedScorer.judgedOf(RUBRIC, run(), counting(), judgements);

        assertThat(judged.pairs()).isEqualTo(4);
        assertThat(judged.counted()).isTrue();
        assertThat(judged.agreementPercent()).isEqualTo(85.0);
        JudgedCriterionScore specific = judged.criteria().getFirst();
        assertThat(specific.candidateWon()).isEqualTo(2);
        assertThat(specific.baselineWon()).isEqualTo(1);
        assertThat(specific.ties()).isEqualTo(1);
        assertThat(specific.net()).isEqualTo(25.0);
        assertThat(specific.worseBeyondTolerance()).isFalse();
        JudgedCriterionScore plain = judged.criteria().get(1);
        assertThat(plain.ties()).isEqualTo(3);
        assertThat(plain.unjudged()).as("a pick the judge left unreadable counts for neither side").isEqualTo(1);
        assertThat(plain.net()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("past ten points against the candidate a criterion is worse, and at ten it is not")
    void isWorseOnlyPastTheTolerance() {
        List<EvalJudgement> atTolerance = new ArrayList<>();
        List<EvalJudgement> pastTolerance = new ArrayList<>();
        List<EvalJudgement> forCandidateAtTolerance = new ArrayList<>();
        List<EvalJudgement> forCandidatePastTolerance = new ArrayList<>();
        for (int repeat = 0; repeat < 10; repeat++) {
            forCandidateAtTolerance.addAll(pair(FIRST, repeat,
                    repeat == 0 ? JudgedWinner.CANDIDATE : JudgedWinner.TIE, JudgedWinner.TIE));
            forCandidatePastTolerance.addAll(pair(FIRST, repeat,
                    repeat < 2 ? JudgedWinner.CANDIDATE : JudgedWinner.TIE, JudgedWinner.TIE));
            atTolerance.addAll(pair(FIRST, repeat, repeat == 0 ? JudgedWinner.BASELINE : JudgedWinner.TIE,
                    JudgedWinner.TIE));
            pastTolerance.addAll(pair(FIRST, repeat, repeat < 2 ? JudgedWinner.BASELINE : JudgedWinner.TIE,
                    JudgedWinner.TIE));
        }

        assertThat(JudgedScorer.judgedOf(RUBRIC, run(), counting(), atTolerance).criteria().getFirst()
                .worseBeyondTolerance()).isFalse();
        JudgedCriterionScore worse = JudgedScorer.judgedOf(RUBRIC, run(), counting(), pastTolerance).criteria()
                .getFirst();
        assertThat(worse.net()).isEqualTo(-20.0);
        assertThat(worse.worseBeyondTolerance()).isTrue();
        assertThat(worse.betterBeyondTolerance()).isFalse();
        assertThat(JudgedScorer.judgedOf(RUBRIC, run(), counting(), forCandidateAtTolerance).criteria().getFirst()
                .betterBeyondTolerance()).as("the same edge the other way").isFalse();
        assertThat(JudgedScorer.judgedOf(RUBRIC, run(), counting(), forCandidatePastTolerance).criteria()
                .getFirst().betterBeyondTolerance()).isTrue();
    }

    @Test
    @DisplayName("a case is told by the side most of its pairs went to, with the judge's reason on the first")
    void tellsACaseByItsPairs() {
        List<EvalJudgement> ofCase = new ArrayList<>();
        ofCase.addAll(pair(FIRST, 0, JudgedWinner.BASELINE, JudgedWinner.TIE));
        ofCase.addAll(pair(FIRST, 1, JudgedWinner.BASELINE, JudgedWinner.CANDIDATE));
        ofCase.addAll(pair(FIRST, 2, JudgedWinner.CANDIDATE, JudgedWinner.CANDIDATE));

        List<EvalCaseJudgedCriterion> judged = JudgedScorer.ofCase(RUBRIC, ofCase);

        assertThat(judged.getFirst().result()).isEqualTo(JudgedWinner.BASELINE);
        assertThat(judged.getFirst().pairs()).isEqualTo(2);
        assertThat(judged.getFirst().of()).isEqualTo(3);
        assertThat(judged.getFirst().reason()).isEqualTo("Reason on repeat 0");
        assertThat(judged.get(1).result()).isEqualTo(JudgedWinner.CANDIDATE);
        assertThat(JudgedScorer.outcomeOf(judged)).isEqualTo(EvalCaseOutcome.REGRESSION);
        assertThat(JudgedScorer.orderOf(ofCase)).containsExactly(JudgePick.A, JudgePick.B, JudgePick.A);
    }

    @Test
    @DisplayName("a prompt with no checks has a result only while its rubric counts")
    void aJudgeOnlyRunNeedsACountingRubric() {
        List<EvalJudgement> judgements = new ArrayList<>();
        for (int repeat = 0; repeat < 4; repeat++) {
            judgements.addAll(pair(FIRST, repeat, JudgedWinner.BASELINE, JudgedWinner.TIE));
            judgements.addAll(pair(SECOND, repeat, JudgedWinner.TIE, JudgedWinner.TIE));
        }
        EvalSubject judgeOnly = subject(List.of());

        EvalRunSummary shownOnly = EvalRunScorer.summarise(judgeOnly, CASES, List.of(), RUBRIC, judgements,
                JudgedScorer.judgedOf(RUBRIC, run(), new RubricStanding(RubricState.UNLABELLED, null, null, null),
                        judgements));
        EvalRunSummary counted = EvalRunScorer.summarise(judgeOnly, CASES, List.of(), RUBRIC, judgements,
                JudgedScorer.judgedOf(RUBRIC, run(), counting(), judgements));

        assertThat(EvalRunScorer.verdictOf(judgeOnly, shownOnly)).as("nothing counts, so no result").isNull();
        assertThat(EvalRunScorer.verdictOf(judgeOnly, counted)).isEqualTo(EvalRunVerdict.FAIL);
        assertThat(counted.regressedCases()).isEqualTo(1);
        assertThat(counted.unchangedCases()).isEqualTo(1);
        assertThat(EvalRunScorer.changedCases(judgeOnly, CASES, List.of(), RUBRIC, judgements))
                .singleElement()
                .satisfies(changed -> {
                    assertThat(changed.key()).isEqualTo("OP-1");
                    assertThat(changed.outcome()).isEqualTo(EvalCaseOutcome.REGRESSION);
                    assertThat(changed.judged()).hasSize(2);
                    assertThat(changed.candidateShownAs()).hasSize(4);
                });
    }

    @Test
    @DisplayName("a prompt with checks keeps its verdict from them while the judged criteria are shown only")
    void shownOnlyCriteriaNeverChangeAVerdict() {
        List<EvalJudgement> judgements = new ArrayList<>();
        for (int repeat = 0; repeat < 4; repeat++) {
            judgements.addAll(pair(FIRST, repeat, JudgedWinner.BASELINE, JudgedWinner.BASELINE));
        }
        EvalSubject checked = subject(List.of(EvalCheck.of("always", "Always passes",
                (input, expected, answer) -> EvalCheckVerdict.PASSED)));

        EvalRunSummary shownOnly = EvalRunScorer.summarise(checked, CASES, List.of(), RUBRIC, judgements,
                JudgedScorer.judgedOf(RUBRIC, run(), new RubricStanding(RubricState.SHOWN_ONLY, 20, 40, null),
                        judgements));
        EvalRunSummary counted = EvalRunScorer.summarise(checked, CASES, List.of(), RUBRIC, judgements,
                JudgedScorer.judgedOf(RUBRIC, run(), counting(), judgements));

        assertThat(shownOnly.judged().criteria().getFirst().worseBeyondTolerance()).isTrue();
        assertThat(EvalRunScorer.verdictOf(checked, shownOnly)).isEqualTo(EvalRunVerdict.PASS);
        assertThat(EvalRunScorer.verdictOf(checked, counted)).isEqualTo(EvalRunVerdict.FAIL);
    }

    private static RubricStanding counting() {
        return new RubricStanding(RubricState.COUNTS, 34, 40, null);
    }

    /** One pair's picks on the two criteria; the candidate is shown as A on even repeats. */
    private static List<EvalJudgement> pair(UUID caseId, int repeat, JudgedWinner specific, JudgedWinner plain) {
        JudgePick shownAs = repeat % 2 == 0 ? JudgePick.A : JudgePick.B;
        return List.of(
                new EvalJudgement(caseId, repeat, "specific", shownAs, specific, "Reason on repeat " + repeat),
                new EvalJudgement(caseId, repeat, "plain", shownAs, plain, plain == null ? null : "Plain enough"));
    }

    private static EvalRun run() {
        return new EvalRun(UUID.randomUUID(), 7, "outreach-opener", RUBRIC.datasetId(), "Openers",
                EvalCaseSplit.HOLDOUT, 2, 1, null, null, 3, EvalRunStatus.RUNNING, null, null, true, 2, 18, 18, 200,
                0, 0, null, RUBRIC.id(), 4, "gemini-3.8-flash", null, null, Instant.now(), Instant.now(), null);
    }

    private static EvalSubject subject(List<EvalCheck> checks) {
        return new EvalSubject() {
            @Override
            public String promptId() {
                return "outreach-opener";
            }

            @Override
            public List<String> problemsOf(JsonNode input, JsonNode expected) {
                return List.of();
            }

            @Override
            public void answer(JsonNode input) {
            }

            @Override
            public List<EvalCheck> checks() {
                return checks;
            }
        };
    }
}
