package app.lightmove.api.prompteval.service;

import app.lightmove.api.core.llm.constant.EvalCheckVerdict;
import app.lightmove.api.core.llm.model.EvalReading;
import app.lightmove.api.core.llm.service.EvalCheck;
import app.lightmove.api.core.llm.service.EvalSubject;
import app.lightmove.api.prompteval.constant.EvalCaseOutcome;
import app.lightmove.api.prompteval.constant.EvalRunSide;
import app.lightmove.api.prompteval.dto.EvalCaseCheckChange;
import app.lightmove.api.prompteval.dto.EvalReadingComparison;
import app.lightmove.api.prompteval.dto.EvalReadingSide;
import app.lightmove.api.prompteval.dto.EvalRunCaseComparison;
import app.lightmove.api.prompteval.dto.EvalCheckScore;
import app.lightmove.api.prompteval.model.EvalRunAnswer;
import app.lightmove.api.prompteval.model.EvalRunCase;
import app.lightmove.api.prompteval.dto.EvalRunSummary;
import app.lightmove.api.prompteval.dto.EvalSideStability;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a run's stored answers into what it found. It reads the verdicts stored with them and asks the model
 * nothing, so the same answers always score the same.
 */
final class EvalRunScorer {

    private static final double PERCENT = 100;
    /** Two rates a rounding error apart are the same rate. */
    private static final double EPSILON = 1e-9;

    private EvalRunScorer() {
    }

    static EvalRunSummary summarise(EvalSubject subject, List<EvalRunCase> cases, List<EvalRunAnswer> answers) {
        Map<UUID, Map<EvalRunSide, List<EvalRunAnswer>>> byCase = byCaseAndSide(answers);
        double tolerance = subject.tolerancePoints();
        List<EvalCheckScore> scores = new ArrayList<>();
        for (EvalCheck check : subject.checks()) {
            Double candidate = passRateOf(check, EvalRunSide.CANDIDATE, answers);
            Double baseline = passRateOf(check, EvalRunSide.BASELINE, answers);
            boolean comparable = candidate != null && baseline != null;
            scores.add(new EvalCheckScore(check.id(), check.label(), rounded(candidate), rounded(baseline),
                    comparable ? rounded(candidate - baseline) : null,
                    comparable && baseline - candidate > tolerance + EPSILON));
        }
        int regressed = 0;
        int better = 0;
        for (EvalRunCase evalCase : cases) {
            EvalCaseOutcome outcome = outcomeOf(changesOf(subject, byCase.get(evalCase.id())));
            regressed += outcome == EvalCaseOutcome.REGRESSION ? 1 : 0;
            better += outcome == EvalCaseOutcome.BETTER ? 1 : 0;
        }
        return new EvalRunSummary(tolerance, scores, stabilityOf(subject, EvalRunSide.CANDIDATE, cases, byCase),
                stabilityOf(subject, EvalRunSide.BASELINE, cases, byCase), regressed, better,
                cases.size() - regressed - better);
    }

    static boolean isNoWorse(EvalRunSummary summary) {
        return summary.checks().stream().noneMatch(EvalCheckScore::worseBeyondTolerance);
    }

    /** The cases the two versions did differently on, regressions first, then by key. */
    static List<EvalRunCaseComparison> changedCases(EvalSubject subject, List<EvalRunCase> cases,
                                                    List<EvalRunAnswer> answers) {
        Map<UUID, Map<EvalRunSide, List<EvalRunAnswer>>> byCase = byCaseAndSide(answers);
        List<EvalRunCaseComparison> changed = new ArrayList<>();
        for (EvalRunCase evalCase : cases) {
            Map<EvalRunSide, List<EvalRunAnswer>> bySide = byCase.get(evalCase.id());
            List<EvalCaseCheckChange> changes = changesOf(subject, bySide);
            EvalCaseOutcome outcome = outcomeOf(changes);
            if (outcome != null) {
                changed.add(new EvalRunCaseComparison(evalCase.id(), evalCase.key(), evalCase.name(), evalCase.tag(),
                        outcome, changes, readingsOf(subject, evalCase, bySide)));
            }
        }
        changed.sort(Comparator.comparing((EvalRunCaseComparison compared) ->
                compared.outcome() != EvalCaseOutcome.REGRESSION).thenComparing(EvalRunCaseComparison::key));
        return changed;
    }

    /**
     * The share of one version's answers that pass, taken within each repeat and averaged over the repeats, so a
     * run of three is one rate with three samples behind it. Null when no answer gave the check anything to judge.
     */
    private static Double passRateOf(EvalCheck check, EvalRunSide side, List<EvalRunAnswer> answers) {
        Map<Integer, int[]> passedAndJudgedByRepeat = new HashMap<>();
        for (EvalRunAnswer answer : answers) {
            EvalCheckVerdict verdict = answer.verdicts().get(check.id());
            if (answer.side() != side || verdict == null || verdict == EvalCheckVerdict.NOT_APPLICABLE) {
                continue;
            }
            int[] counts = passedAndJudgedByRepeat.computeIfAbsent(answer.repeat(), repeat -> new int[2]);
            counts[0] += verdict == EvalCheckVerdict.PASSED ? 1 : 0;
            counts[1]++;
        }
        return passedAndJudgedByRepeat.values().stream()
                .mapToDouble(counts -> PERCENT * counts[0] / counts[1])
                .average()
                .stream().boxed().findFirst().orElse(null);
    }

    /** The checks one case's answers passed a different number of times for the two versions. */
    private static List<EvalCaseCheckChange> changesOf(EvalSubject subject,
                                                       Map<EvalRunSide, List<EvalRunAnswer>> bySide) {
        List<EvalRunAnswer> candidate = answersOf(bySide, EvalRunSide.CANDIDATE);
        List<EvalRunAnswer> baseline = answersOf(bySide, EvalRunSide.BASELINE);
        List<EvalCaseCheckChange> changes = new ArrayList<>();
        for (EvalCheck check : subject.checks()) {
            int candidatePassed = passedIn(candidate, check);
            int baselinePassed = passedIn(baseline, check);
            if (candidatePassed != baselinePassed) {
                changes.add(new EvalCaseCheckChange(check.id(), check.label(), candidatePassed, baselinePassed,
                        Math.max(candidate.size(), baseline.size())));
            }
        }
        return changes;
    }

    /** A case that lost on any check regressed, whatever it gained elsewhere. Null: nothing changed. */
    private static EvalCaseOutcome outcomeOf(List<EvalCaseCheckChange> changes) {
        if (changes.isEmpty()) {
            return null;
        }
        return changes.stream().anyMatch(change -> change.candidatePassed() < change.baselinePassed())
                ? EvalCaseOutcome.REGRESSION : EvalCaseOutcome.BETTER;
    }

    private static int passedIn(List<EvalRunAnswer> answers, EvalCheck check) {
        return (int) answers.stream()
                .filter(answer -> answer.verdicts().get(check.id()) == EvalCheckVerdict.PASSED)
                .count();
    }

    private static EvalSideStability stabilityOf(EvalSubject subject, EvalRunSide side, List<EvalRunCase> cases,
                                                 Map<UUID, Map<EvalRunSide, List<EvalRunAnswer>>> byCase) {
        int measured = 0;
        int same = 0;
        List<Double> spreads = new ArrayList<>();
        for (EvalRunCase evalCase : cases) {
            List<EvalRunAnswer> answers = answersOf(byCase.get(evalCase.id()), side);
            if (answers.size() < 2) {
                continue;
            }
            Map<String, List<EvalReading>> readings = readingsByKey(subject, evalCase, answers);
            if (readings.isEmpty()) {
                measured++;
                same += answers.stream().map(EvalRunAnswer::answer).distinct().count() == 1 ? 1 : 0;
                continue;
            }
            for (List<EvalReading> ofOneThing : readings.values()) {
                measured++;
                // A repeat that left the thing out differs from one that scored it.
                boolean scoredEveryTime = ofOneThing.size() == answers.size();
                same += scoredEveryTime && ofOneThing.stream().map(EvalReading::score).distinct().count() == 1
                        ? 1 : 0;
                Double spread = spreadOf(scoresOf(ofOneThing));
                if (spread != null) {
                    spreads.add(spread);
                }
            }
        }
        return new EvalSideStability(measured == 0 ? null : rounded(PERCENT * same / measured),
                spreads.isEmpty() ? null
                        : rounded(spreads.stream().mapToDouble(Double::doubleValue).average().orElseThrow()));
    }

    private static List<EvalReadingComparison> readingsOf(EvalSubject subject, EvalRunCase evalCase,
                                                          Map<EvalRunSide, List<EvalRunAnswer>> bySide) {
        Map<String, List<EvalReading>> candidate =
                readingsByKey(subject, evalCase, answersOf(bySide, EvalRunSide.CANDIDATE));
        Map<String, List<EvalReading>> baseline =
                readingsByKey(subject, evalCase, answersOf(bySide, EvalRunSide.BASELINE));
        Set<String> keys = new LinkedHashSet<>(candidate.keySet());
        keys.addAll(baseline.keySet());
        List<EvalReadingComparison> compared = new ArrayList<>();
        for (String key : keys) {
            List<EvalReading> ofCandidate = candidate.getOrDefault(key, List.of());
            List<EvalReading> ofBaseline = baseline.getOrDefault(key, List.of());
            EvalReading named = ofCandidate.isEmpty() ? ofBaseline.getFirst() : ofCandidate.getFirst();
            compared.add(new EvalReadingComparison(key, named.label(), named.expected(), sideOf(ofCandidate),
                    sideOf(ofBaseline)));
        }
        return compared;
    }

    private static EvalReadingSide sideOf(List<EvalReading> readings) {
        List<Double> scores = scoresOf(readings);
        long outside = readings.stream().filter(EvalReading::outside).count();
        return new EvalReadingSide(
                scores.isEmpty() ? null
                        : rounded(scores.stream().mapToDouble(Double::doubleValue).average().orElseThrow()),
                rounded(spreadOf(scores)),
                outside * 2 > readings.size(),
                readings.stream().map(EvalReading::text).filter(Objects::nonNull).findFirst().orElse(null));
    }

    /** What each repeat's answer said about each scored thing, in the order the first answer lists them. */
    private static Map<String, List<EvalReading>> readingsByKey(EvalSubject subject, EvalRunCase evalCase,
                                                                List<EvalRunAnswer> answers) {
        Map<String, List<EvalReading>> byKey = new LinkedHashMap<>();
        for (EvalRunAnswer answer : answers) {
            for (EvalReading reading : subject.readingsOf(evalCase.input(), evalCase.expected(), answer.answer())) {
                byKey.computeIfAbsent(reading.key(), key -> new ArrayList<>()).add(reading);
            }
        }
        return byKey;
    }

    private static List<Double> scoresOf(List<EvalReading> readings) {
        return readings.stream().map(EvalReading::score).filter(Objects::nonNull).toList();
    }

    /** The standard deviation of the scores. Null with fewer than two: one score has no spread. */
    private static Double spreadOf(List<Double> scores) {
        if (scores.size() < 2) {
            return null;
        }
        double mean = scores.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        return Math.sqrt(scores.stream().mapToDouble(score -> (score - mean) * (score - mean)).sum()
                / scores.size());
    }

    private static Map<UUID, Map<EvalRunSide, List<EvalRunAnswer>>> byCaseAndSide(List<EvalRunAnswer> answers) {
        Map<UUID, Map<EvalRunSide, List<EvalRunAnswer>>> byCase = new HashMap<>();
        answers.stream()
                .sorted(Comparator.comparingInt(EvalRunAnswer::repeat))
                .forEach(answer -> byCase
                        .computeIfAbsent(answer.caseId(), caseId -> new EnumMap<>(EvalRunSide.class))
                        .computeIfAbsent(answer.side(), side -> new ArrayList<>())
                        .add(answer));
        return byCase;
    }

    private static List<EvalRunAnswer> answersOf(Map<EvalRunSide, List<EvalRunAnswer>> bySide, EvalRunSide side) {
        return bySide == null ? List.of() : bySide.getOrDefault(side, List.of());
    }

    private static Double rounded(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }
}
