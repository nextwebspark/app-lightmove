package app.lightmove.api.prompteval.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.llm.constant.EvalCheckVerdict;
import app.lightmove.api.core.llm.model.EvalReading;
import app.lightmove.api.core.llm.service.EvalCheck;
import app.lightmove.api.core.llm.service.EvalSubject;
import app.lightmove.api.prompteval.constant.EvalCaseOutcome;
import app.lightmove.api.prompteval.constant.EvalRunSide;
import app.lightmove.api.prompteval.dto.EvalReadingComparison;
import app.lightmove.api.prompteval.dto.EvalRunCaseComparison;
import app.lightmove.api.prompteval.model.EvalCheckScore;
import app.lightmove.api.prompteval.model.EvalRunAnswer;
import app.lightmove.api.prompteval.model.EvalRunCase;
import app.lightmove.api.prompteval.model.EvalRunSummary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

/** What a run's stored answers add up to: the pass rates, the verdict's tolerance, the spread and the changed cases. */
class EvalRunScorerTest {

    private static final String RIGHT = "right";
    private static final String SOURCED = "sourced";
    private static final EvalRunCase FIRST = evalCase("C-1");
    private static final EvalRunCase SECOND = evalCase("C-2");
    private static final List<EvalRunCase> CASES = List.of(FIRST, SECOND);

    @Test
    @DisplayName("a check's pass rate is the mean of each repeat's share, and a fall past the tolerance is worse")
    void aFallPastTheToleranceIsWorse() {
        List<EvalRunAnswer> answers = new Answers()
                .both(FIRST, 0, EvalCheckVerdict.PASSED).both(SECOND, 0, EvalCheckVerdict.PASSED)
                .both(FIRST, 1, EvalCheckVerdict.PASSED)
                .one(SECOND, EvalRunSide.BASELINE, 1, EvalCheckVerdict.PASSED)
                .one(SECOND, EvalRunSide.CANDIDATE, 1, EvalCheckVerdict.FAILED)
                .all();

        EvalRunSummary summary = EvalRunScorer.summarise(subject(3), CASES, answers);

        EvalCheckScore right = summary.checks().getFirst();
        assertThat(right.candidate()).isEqualTo(75.0);
        assertThat(right.baseline()).isEqualTo(100.0);
        assertThat(right.change()).isEqualTo(-25.0);
        assertThat(right.worseBeyondTolerance()).isTrue();
        assertThat(EvalRunScorer.isNoWorse(summary)).isFalse();
        assertThat(summary.regressedCases()).isEqualTo(1);
        assertThat(summary.unchangedCases()).isEqualTo(1);
    }

    @Test
    @DisplayName("the same fall inside the prompt's tolerance leaves the run no worse, with its regression counted")
    void aFallInsideTheToleranceIsNoWorse() {
        List<EvalRunAnswer> answers = new Answers()
                .both(FIRST, 0, EvalCheckVerdict.PASSED)
                .one(SECOND, EvalRunSide.BASELINE, 0, EvalCheckVerdict.PASSED)
                .one(SECOND, EvalRunSide.CANDIDATE, 0, EvalCheckVerdict.FAILED)
                .all();

        EvalRunSummary summary = EvalRunScorer.summarise(subject(50), CASES, answers);

        assertThat(summary.checks().getFirst().change()).isEqualTo(-50.0);
        assertThat(summary.checks().getFirst().worseBeyondTolerance()).isFalse();
        assertThat(EvalRunScorer.isNoWorse(summary)).isTrue();
        assertThat(summary.regressedCases()).isEqualTo(1);
    }

    @Test
    @DisplayName("an answer a check had nothing to judge in is left out of its rate, and a side with none is not worse")
    void nothingToJudgeIsNotAFailure() {
        List<EvalRunAnswer> answers = new Answers()
                .one(FIRST, EvalRunSide.BASELINE, 0, EvalCheckVerdict.PASSED)
                .one(FIRST, EvalRunSide.CANDIDATE, 0, EvalCheckVerdict.NOT_APPLICABLE)
                .one(SECOND, EvalRunSide.BASELINE, 0, EvalCheckVerdict.NOT_APPLICABLE)
                .one(SECOND, EvalRunSide.CANDIDATE, 0, EvalCheckVerdict.NOT_APPLICABLE)
                .all();

        EvalCheckScore right = EvalRunScorer.summarise(subject(0), CASES, answers).checks().getFirst();

        assertThat(right.baseline()).isEqualTo(100.0);
        assertThat(right.candidate()).isNull();
        assertThat(right.change()).isNull();
        assertThat(right.worseBeyondTolerance()).isFalse();
    }

    @Test
    @DisplayName("the changed cases come regressions first, each with the checks that moved and both sides' scores")
    void changedCasesAreListedRegressionsFirst() {
        EvalRunCase third = evalCase("C-3");
        List<EvalRunAnswer> answers = new Answers()
                .scored(FIRST, EvalRunSide.BASELINE, 0, EvalCheckVerdict.FAILED, 8)
                .scored(FIRST, EvalRunSide.CANDIDATE, 0, EvalCheckVerdict.PASSED, 7)
                .scored(SECOND, EvalRunSide.BASELINE, 0, EvalCheckVerdict.PASSED, 8)
                .scored(SECOND, EvalRunSide.BASELINE, 1, EvalCheckVerdict.PASSED, 6)
                .scored(SECOND, EvalRunSide.CANDIDATE, 0, EvalCheckVerdict.FAILED, 12)
                .scored(SECOND, EvalRunSide.CANDIDATE, 1, EvalCheckVerdict.FAILED, 12)
                .both(third, 0, EvalCheckVerdict.PASSED)
                .all();

        List<EvalRunCaseComparison> changed =
                EvalRunScorer.changedCases(subject(0), List.of(FIRST, SECOND, third), answers);

        assertThat(changed).extracting(EvalRunCaseComparison::key).containsExactly("C-2", "C-1");
        EvalRunCaseComparison regressed = changed.getFirst();
        assertThat(regressed.outcome()).isEqualTo(EvalCaseOutcome.REGRESSION);
        assertThat(regressed.checks()).singleElement().satisfies(change -> {
            assertThat(change.id()).isEqualTo(RIGHT);
            assertThat(change.candidatePassed()).isZero();
            assertThat(change.baselinePassed()).isEqualTo(2);
            assertThat(change.repeats()).isEqualTo(2);
        });
        EvalReadingComparison reading = regressed.readings().getFirst();
        assertThat(reading.expected()).isEqualTo("up to 10");
        assertThat(reading.baseline().mean()).isEqualTo(7.0);
        assertThat(reading.baseline().spread()).isEqualTo(1.0);
        assertThat(reading.baseline().outside()).isFalse();
        assertThat(reading.candidate().mean()).isEqualTo(12.0);
        assertThat(reading.candidate().spread()).isEqualTo(0.0);
        assertThat(reading.candidate().outside()).isTrue();
        assertThat(reading.candidate().text()).isEqualTo("scored 12");
        assertThat(changed.getLast().outcome()).isEqualTo(EvalCaseOutcome.BETTER);
    }

    @Test
    @DisplayName("stability is how many scores held across the repeats and how far the rest moved")
    void stabilityIsMeasuredAcrossRepeats() {
        List<EvalRunAnswer> answers = new Answers()
                .scored(FIRST, EvalRunSide.CANDIDATE, 0, EvalCheckVerdict.PASSED, 6)
                .scored(FIRST, EvalRunSide.CANDIDATE, 1, EvalCheckVerdict.PASSED, 6)
                .scored(SECOND, EvalRunSide.CANDIDATE, 0, EvalCheckVerdict.PASSED, 5)
                .scored(SECOND, EvalRunSide.CANDIDATE, 1, EvalCheckVerdict.PASSED, 9)
                .scored(FIRST, EvalRunSide.BASELINE, 0, EvalCheckVerdict.PASSED, 6)
                .all();

        EvalRunSummary summary = EvalRunScorer.summarise(subject(0), CASES, answers);

        assertThat(summary.candidate().sameAnswerRate()).isEqualTo(50.0);
        assertThat(summary.candidate().scoreSpread()).isEqualTo(1.0);
        assertThat(summary.baseline().sameAnswerRate()).isNull();
        assertThat(summary.baseline().scoreSpread()).isNull();
    }

    private static EvalRunCase evalCase(String key) {
        return new EvalRunCase(UUID.randomUUID(), key, "Case " + key, null, JsonNodeFactory.instance.objectNode(),
                null);
    }

    /** Two checks whose verdicts the answers carry, and one scored thing read off an answer's {@code score}. */
    private static EvalSubject subject(double tolerance) {
        return new EvalSubject() {
            @Override
            public String promptId() {
                return "test-prompt";
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
                return List.of(
                        EvalCheck.of(RIGHT, "Answer is right", (input, expected, answer) -> EvalCheckVerdict.PASSED),
                        EvalCheck.of(SOURCED, "Answer names its source",
                                (input, expected, answer) -> EvalCheckVerdict.PASSED));
            }

            @Override
            public double tolerancePoints() {
                return tolerance;
            }

            @Override
            public List<EvalReading> readingsOf(JsonNode input, JsonNode expected, JsonNode answer) {
                if (answer == null) {
                    return List.of();
                }
                double score = answer.get("score").asDouble();
                return List.of(new EvalReading("T1", "Financial stewardship", "up to 10", score,
                        "scored " + answer.get("score").asInt(), score > 10));
            }
        };
    }

    /** Builds a run's answers; the second check passes throughout, so only the first one moves. */
    private static final class Answers {

        private final List<EvalRunAnswer> answers = new ArrayList<>();

        Answers both(EvalRunCase evalCase, int repeat, EvalCheckVerdict right) {
            return one(evalCase, EvalRunSide.BASELINE, repeat, right).one(evalCase, EvalRunSide.CANDIDATE, repeat,
                    right);
        }

        Answers one(EvalRunCase evalCase, EvalRunSide side, int repeat, EvalCheckVerdict right) {
            answers.add(new EvalRunAnswer(evalCase.id(), side, repeat, null, verdicts(right)));
            return this;
        }

        Answers scored(EvalRunCase evalCase, EvalRunSide side, int repeat, EvalCheckVerdict right, int score) {
            answers.add(new EvalRunAnswer(evalCase.id(), side, repeat,
                    JsonNodeFactory.instance.objectNode().put("score", score), verdicts(right)));
            return this;
        }

        List<EvalRunAnswer> all() {
            return answers;
        }

        private static Map<String, EvalCheckVerdict> verdicts(EvalCheckVerdict right) {
            Map<String, EvalCheckVerdict> verdicts = new LinkedHashMap<>();
            verdicts.put(RIGHT, right);
            verdicts.put(SOURCED, EvalCheckVerdict.PASSED);
            return verdicts;
        }
    }
}
