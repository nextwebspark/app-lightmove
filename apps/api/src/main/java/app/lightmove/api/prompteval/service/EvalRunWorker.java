package app.lightmove.api.prompteval.service;

import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PlatformEvalSettings;
import app.lightmove.api.core.llm.constant.EvalCheckVerdict;
import app.lightmove.api.core.llm.model.PromptTrialResult;
import app.lightmove.api.core.llm.model.ServedPrompt;
import app.lightmove.api.core.llm.service.EvalCheck;
import app.lightmove.api.core.llm.service.EvalSubject;
import app.lightmove.api.core.llm.service.PromptRegistryStore;
import app.lightmove.api.core.llm.service.PromptTrialRunner;
import app.lightmove.api.prompteval.constant.EvalRunFailure;
import app.lightmove.api.prompteval.constant.EvalRunSide;
import app.lightmove.api.prompteval.constant.EvalRunStatus;
import app.lightmove.api.prompteval.constant.EvalRunVerdict;
import app.lightmove.api.prompteval.constant.JudgePick;
import app.lightmove.api.prompteval.constant.JudgedWinner;
import app.lightmove.api.prompteval.model.ClaimedEvalRun;
import app.lightmove.api.prompteval.model.EvalJudgement;
import app.lightmove.api.prompteval.model.EvalRubric;
import app.lightmove.api.prompteval.model.EvalRun;
import app.lightmove.api.prompteval.model.EvalRunAnswer;
import app.lightmove.api.prompteval.model.EvalRunCase;
import app.lightmove.api.prompteval.model.EvalRunJudged;
import app.lightmove.api.prompteval.model.EvalRunPulse;
import app.lightmove.api.prompteval.model.EvalRunQueued;
import app.lightmove.api.prompteval.model.EvalRunSummary;
import app.lightmove.api.prompteval.model.JudgeReading;
import app.lightmove.api.prompteval.model.JudgedPick;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Answers a run's cases with both versions, off every request: woken when a start commits, and by a timer for a run
 * no start handed over (a restart, a worker gone quiet). Where the dataset has a rubric, the judge then compares each
 * pair of answers, and its calls are the run's too.
 *
 * <p>One thread of its own ({@link ClaimedJobDrainer}), which makes every call itself, because
 * {@link PromptTrialRunner} swaps the version per thread, and carries no workspace: a run sits inside none.
 */
@Slf4j
@Component
public class EvalRunWorker {

    /** How often one answer is asked for before the run gives up on the model. */
    static final int ASKS_PER_ANSWER = 3;

    private final EvalRunStore store;
    private final EvalRubricStore rubrics;
    private final EvalSubjects subjects;
    private final EvalJudge judge;
    private final JudgeCalibrationService calibration;
    private final PromptRegistryStore registry;
    private final PromptTrialRunner runner;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final Clock clock;
    private final PlatformEvalSettings settings;
    private final ClaimedJobDrainer<ClaimedEvalRun> drainer;

    EvalRunWorker(EvalRunStore store, EvalRubricStore rubrics, EvalSubjects subjects, EvalJudge judge,
                  JudgeCalibrationService calibration, PromptRegistryStore registry, PromptTrialRunner runner,
                  TransactionTemplate transactions, AuditService audit, Clock clock, LightMoveProperties properties) {
        this.store = store;
        this.rubrics = rubrics;
        this.subjects = subjects;
        this.judge = judge;
        this.calibration = calibration;
        this.registry = registry;
        this.runner = runner;
        this.transactions = transactions;
        this.audit = audit;
        this.clock = clock;
        this.settings = properties.platform().eval();
        this.drainer = new ClaimedJobDrainer<>("eval-run", () -> store.claim(staleBefore()), this::work,
                () -> store.isOneWaiting(staleBefore()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void queued(EvalRunQueued queued) {
        wake();
    }

    /** Hands the look for a waiting run to the worker's thread and returns: the scheduler's threads are shared. */
    @Scheduled(fixedDelayString = "${lightmove.platform.eval.worker-tick}",
            initialDelayString = "${lightmove.platform.eval.worker-tick}")
    public void wake() {
        drainer.wake();
    }

    /** No run is being worked here: what a test waits for. */
    public boolean isIdle() {
        return drainer.isIdle();
    }

    private void work(ClaimedEvalRun claimed) {
        EvalRun run = claimed.run();
        try {
            end(claimed, answerAll(claimed));
        } catch (RuntimeException failure) {
            if (drainer.isShuttingDown()) {
                return;
            }
            log.error("Eval run {} stopped unexpectedly", run.number(), failure);
            end(claimed, new Ending.Failed(EvalRunFailure.STOPPED_UNEXPECTEDLY));
        }
    }

    private Ending answerAll(ClaimedEvalRun claimed) {
        EvalRun run = claimed.run();
        EvalSubject subject = subjects.of(run.promptId())
                .orElseThrow(() -> new IllegalStateException("No eval subject for prompt " + run.promptId()));
        Map<EvalRunSide, ServedPrompt> versions = Map.of(
                EvalRunSide.CANDIDATE, versionOf(run, run.candidateVersion()),
                EvalRunSide.BASELINE, versionOf(run, run.baselineVersion()));
        EvalRubric rubric = run.rubricId() == null ? null : rubrics.find(run.rubricId())
                .orElseThrow(() -> new IllegalStateException("No rubric " + run.rubricId()));
        ServedPrompt judgeVersion = rubric == null ? null : judgeOf(run);
        List<EvalRunCase> cases = store.casesOf(run.id());
        Map<String, EvalRunAnswer> answers = new HashMap<>();
        store.answersOf(run.id()).forEach(answer ->
                answers.put(keyOf(answer.caseId(), answer.side(), answer.repeat()), answer));
        Set<String> judged = new HashSet<>();
        if (rubric != null) {
            rubrics.judgementsOf(run.id()).forEach(judgement ->
                    judged.add(pairKeyOf(judgement.caseId(), judgement.repeat())));
        }

        for (EvalRunCase evalCase : cases) {
            for (int repeat = 0; repeat < run.repeats(); repeat++) {
                for (EvalRunSide side : EvalRunSide.values()) {
                    String key = keyOf(evalCase.id(), side, repeat);
                    if (answers.containsKey(key)) {
                        continue;
                    }
                    Optional<Ending> stopped = stopBeforeCall(claimed);
                    if (stopped.isPresent()) {
                        return stopped.get();
                    }
                    EvalRunAnswer answered = answer(run, subject, versions.get(side), evalCase, side, repeat);
                    if (drainer.isShuttingDown()) {
                        return new Ending.Left();
                    }
                    if (answered == null) {
                        return new Ending.Failed(EvalRunFailure.MODEL_UNAVAILABLE);
                    }
                    answers.put(key, answered);
                }
                if (rubric == null || judged.contains(pairKeyOf(evalCase.id(), repeat))) {
                    continue;
                }
                Optional<Ending> stopped = stopBeforeCall(claimed);
                if (stopped.isPresent()) {
                    return stopped.get();
                }
                boolean judgedNow = judge(run, rubric, judgeVersion, evalCase, repeat,
                        answers.get(keyOf(evalCase.id(), EvalRunSide.CANDIDATE, repeat)).answer(),
                        answers.get(keyOf(evalCase.id(), EvalRunSide.BASELINE, repeat)).answer());
                if (drainer.isShuttingDown()) {
                    return new Ending.Left();
                }
                if (!judgedNow) {
                    return new Ending.Failed(EvalRunFailure.MODEL_UNAVAILABLE);
                }
            }
        }
        List<EvalJudgement> judgements = rubric == null ? List.of() : rubrics.judgementsOf(run.id());
        // Whether the judged criteria count is read as the run finishes and stored with it: labelling later
        // changes no run. It is read for the judge that made this run's picks, never the one serving by now: a
        // judge put live and checked while the run went says nothing of the picks an unchecked one made (#1322).
        EvalRunJudged judgedSummary = rubric == null ? null : JudgedScorer.judgedOf(rubric,
                store.find(run.id()).orElse(run), calibration.standingFor(rubric, judgeVersion.version()),
                judgements);
        EvalRunSummary summary = EvalRunScorer.summarise(subject, cases, store.answersOf(run.id()), rubric,
                judgements, judgedSummary);
        return new Ending.Finished(summary, EvalRunScorer.verdictOf(subject, summary));
    }

    /**
     * The judge this run's pairs are judged by: the one serving when the run was first worked, kept from then on.
     * A run picked up after a restart, with a new judge live in between, would otherwise hold picks from two.
     */
    private ServedPrompt judgeOf(EvalRun run) {
        int pinned = run.judgeVersion() != null ? run.judgeVersion()
                : store.pinJudgeVersion(run.id(), judge.liveVersion()
                        .orElseThrow(() -> new IllegalStateException("The eval judge has no version serving"))
                        .version());
        return registry.version(EvalJudge.PROMPT_ID, pinned)
                .orElseThrow(() -> new IllegalStateException("No version " + pinned + " of the eval judge"));
    }

    /** Empty: the next call may be made. Asked before every call, a judge's among them. */
    private Optional<Ending> stopBeforeCall(ClaimedEvalRun claimed) {
        EvalRunPulse pulse = store.pulse(claimed);
        if (!pulse.held()) {
            log.info("Eval run {} is no longer this worker's", claimed.run().number());
            return Optional.of(new Ending.Left());
        }
        if (pulse.stopRequested()) {
            return Optional.of(new Ending.Cancelled());
        }
        return pulse.callsMade() >= claimed.run().callCap()
                ? Optional.of(new Ending.Failed(EvalRunFailure.CALL_CAP_REACHED)) : Optional.empty();
    }

    /**
     * Null when the model answered nothing, asked {@link #ASKS_PER_ANSWER} times. Such a call is never stored as
     * an answer: both versions failing alike during an outage would otherwise score as no worse.
     */
    private EvalRunAnswer answer(EvalRun run, EvalSubject subject, ServedPrompt version, EvalRunCase evalCase,
                                 EvalRunSide side, int repeat) {
        for (int ask = 0; ask < ASKS_PER_ANSWER && !drainer.isShuttingDown(); ask++) {
            PromptTrialResult asked = null;
            try {
                asked = runner.run(version, () -> subject.answer(evalCase.input()));
            } catch (RuntimeException unanswered) {
                // The exception's own text is left out: a transport failure can quote what was sent.
                log.warn("Eval run {} case {} got no answer: {}", run.number(), evalCase.key(),
                        unanswered.getClass().getSimpleName());
            }
            if (asked == null || asked.modelCalls() == 0) {
                store.addSpend(run.id(), side, 1, 0, 0, null);
                continue;
            }
            EvalRunAnswer answer = new EvalRunAnswer(evalCase.id(), side, repeat, asked.parsedAnswer(),
                    verdictsOn(subject, evalCase, asked));
            PromptTrialResult stored = asked;
            transactions.executeWithoutResult(status -> {
                store.addAnswer(run.id(), answer, stored);
                store.addSpend(run.id(), side, stored.modelCalls(), stored.inputTokens(), stored.outputTokens(),
                        stored.model());
            });
            return answer;
        }
        return null;
    }

    /**
     * Has the judge compare one pair, the two answers in an order drawn at random and stored with its picks. False
     * when the judge never answered: an outage is never stored as a tie.
     */
    private boolean judge(EvalRun run, EvalRubric rubric, ServedPrompt judgeVersion, EvalRunCase evalCase,
                          int repeat, JsonNode candidate, JsonNode baseline) {
        boolean candidateFirst = ThreadLocalRandom.current().nextBoolean();
        Optional<JudgeReading> answered = judge.judgeUntilAnswered(judgeVersion, rubric.criteria(), evalCase.input(),
                candidateFirst ? candidate : baseline, candidateFirst ? baseline : candidate,
                drainer::isShuttingDown, unanswered -> store.addJudgeSpend(run.id(), unanswered));
        if (answered.isEmpty()) {
            return false;
        }
        JudgeReading reading = answered.get();
        List<EvalJudgement> judgements = rubric.criteria().stream()
                .map(criterion -> {
                    JudgedPick pick = reading.picks().getOrDefault(criterion.key(), JudgedPick.UNREADABLE);
                    return new EvalJudgement(evalCase.id(), repeat, criterion.key(),
                            candidateFirst ? JudgePick.A : JudgePick.B, winnerOf(pick.pick(), candidateFirst),
                            pick.reason());
                })
                .toList();
        transactions.executeWithoutResult(status -> {
            rubrics.addJudgements(run.id(), judgements);
            store.addJudgeSpend(run.id(), reading);
        });
        return true;
    }

    private static JudgedWinner winnerOf(JudgePick pick, boolean candidateFirst) {
        if (pick == null) {
            return null;
        }
        if (pick == JudgePick.TIE) {
            return JudgedWinner.TIE;
        }
        return (pick == JudgePick.A) == candidateFirst ? JudgedWinner.CANDIDATE : JudgedWinner.BASELINE;
    }

    private Map<String, EvalCheckVerdict> verdictsOn(EvalSubject subject, EvalRunCase evalCase,
                                                     PromptTrialResult asked) {
        Map<String, EvalCheckVerdict> verdicts = new LinkedHashMap<>();
        for (EvalCheck check : subject.checks()) {
            EvalCheckVerdict verdict;
            try {
                verdict = check.verdictOn(evalCase.input(), evalCase.expected(), asked.parsedAnswer());
            } catch (RuntimeException unreadable) {
                // An answer a check cannot read is one it does not pass.
                log.warn("Eval check {} could not read an answer to case {}: {}", check.id(), evalCase.key(),
                        unreadable.toString());
                verdict = EvalCheckVerdict.FAILED;
            }
            verdicts.put(check.id(), verdict);
        }
        return verdicts;
    }

    private void end(ClaimedEvalRun claimed, Ending ending) {
        switch (ending) {
            case Ending.Left left -> { }
            // A cancelled run was audited when the stop was asked for, by the person who asked.
            case Ending.Cancelled cancelled -> store.end(claimed, EvalRunStatus.CANCELLED, null);
            case Ending.Failed failed -> {
                if (store.end(claimed, EvalRunStatus.FAILED, failed.failure())) {
                    record(claimed, null, null, failed.failure());
                }
            }
            case Ending.Finished finished -> {
                if (store.finish(claimed, finished.verdict(), finished.summary())) {
                    record(claimed, finished.summary(), finished.verdict(), null);
                }
            }
        }
    }

    private void record(ClaimedEvalRun claimed, EvalRunSummary summary, EvalRunVerdict verdict,
                        EvalRunFailure failure) {
        store.find(claimed.run().id()).ifPresent(run -> {
            AuditService.Builder event = audit.event(PlatformEventType.EVAL_RUN_FINISHED)
                    .actor(run.startedBy())
                    .target("eval_run", run.id())
                    .detail("number", run.number())
                    .detail("prompt", run.promptId())
                    .detail("status", run.status().name())
                    .detailIfPresent("verdict", verdict == null ? null : verdict.name())
                    .detailIfPresent("failure", failure == null ? null : failure.name())
                    .detail("calls", run.callsMade())
                    .detail("tokens", run.inputTokens() + run.outputTokens());
            if (summary != null && summary.judged() != null) {
                event.detail("judgedCounted", summary.judged().counted());
            }
            if (failure != null) {
                event.failed();
            }
            event.record();
        });
    }

    private ServedPrompt versionOf(EvalRun run, int version) {
        return registry.version(run.promptId(), version)
                .orElseThrow(() -> new IllegalStateException(
                        "No version " + version + " of prompt " + run.promptId()));
    }

    private Instant staleBefore() {
        return clock.instant().minus(settings.staleAfter());
    }

    private static String keyOf(Object caseId, EvalRunSide side, int repeat) {
        return caseId + ":" + side + ":" + repeat;
    }

    private static String pairKeyOf(Object caseId, int repeat) {
        return caseId + ":" + repeat;
    }

    @PreDestroy
    void stop() {
        drainer.stop();
    }

    /** How a claimed run's work came to a stop. */
    private sealed interface Ending {

        /** {@code verdict} is null for a run nothing counted on. */
        record Finished(EvalRunSummary summary, EvalRunVerdict verdict) implements Ending { }

        record Cancelled() implements Ending { }

        record Failed(EvalRunFailure failure) implements Ending { }

        /**
         * Not this worker's to end: another instance took the run, or this one is shutting down and leaves it,
         * still running, for the next to pick up from the answers stored.
         */
        record Left() implements Ending { }
    }
}
