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
import app.lightmove.api.prompteval.model.RubricCriterion;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * <p>One thread of its own, never the shared {@code @Async} pool, which runs a task on its publisher's thread once
 * it is full: a run takes minutes. That thread makes every call itself, because {@link PromptTrialRunner} swaps the
 * version per thread, and carries no workspace: a run sits inside none.
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
    private final ExecutorService thread = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("eval-run").daemon(true).factory());
    private final AtomicBoolean working = new AtomicBoolean();
    private volatile boolean shuttingDown;

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
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void queued(EvalRunQueued queued) {
        wake();
    }

    /** Hands the look for a waiting run to the worker's thread and returns: the scheduler's threads are shared. */
    @Scheduled(fixedDelayString = "${lightmove.platform.eval.worker-tick}",
            initialDelayString = "${lightmove.platform.eval.worker-tick}")
    public void wake() {
        if (working.compareAndSet(false, true)) {
            thread.execute(this::drain);
        }
    }

    /** No run is being worked here: what a test waits for. */
    public boolean isIdle() {
        return !working.get();
    }

    /**
     * Looks again after standing down: a run queued while this one was ending found the worker busy, and would
     * otherwise wait for the timer.
     */
    private void drain() {
        boolean lookAgain = true;
        while (lookAgain) {
            try {
                Optional<ClaimedEvalRun> claimed = store.claim(staleBefore());
                while (claimed.isPresent()) {
                    work(claimed.get());
                    claimed = store.claim(staleBefore());
                }
            } catch (RuntimeException failure) {
                log.error("The eval worker could not look for a run; the timer looks again", failure);
            } finally {
                working.set(false);
            }
            lookAgain = isOneWaiting() && working.compareAndSet(false, true);
        }
    }

    private boolean isOneWaiting() {
        try {
            return store.isOneWaiting(staleBefore());
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private void work(ClaimedEvalRun claimed) {
        EvalRun run = claimed.run();
        try {
            end(claimed, answerAll(claimed));
        } catch (RuntimeException failure) {
            if (shuttingDown) {
                return;
            }
            log.error("Eval run {} stopped unexpectedly", run.number(), failure);
            end(claimed, Ending.failed(EvalRunFailure.STOPPED_UNEXPECTEDLY));
        }
    }

    /**
     * Null: the run is no longer this worker's to end. Another instance took it, or this one is shutting down and
     * leaves it, still running, for the next to pick up from the answers stored.
     */
    private Ending answerAll(ClaimedEvalRun claimed) {
        EvalRun run = claimed.run();
        EvalSubject subject = subjects.of(run.promptId())
                .orElseThrow(() -> new IllegalStateException("No eval subject for prompt " + run.promptId()));
        Map<EvalRunSide, ServedPrompt> versions = Map.of(
                EvalRunSide.CANDIDATE, versionOf(run, run.candidateVersion()),
                EvalRunSide.BASELINE, versionOf(run, run.baselineVersion()));
        EvalRubric rubric = run.rubricId() == null ? null : rubrics.find(run.rubricId())
                .orElseThrow(() -> new IllegalStateException("No rubric " + run.rubricId()));
        ServedPrompt judgeVersion = rubric == null ? null : judge.liveVersion()
                .orElseThrow(() -> new IllegalStateException("The eval judge has no version serving"));
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
                    Ending stopped = stopBeforeCall(claimed);
                    if (stopped != null) {
                        return stopped == Ending.NOT_HELD ? null : stopped;
                    }
                    EvalRunAnswer answered = answer(run, subject, versions.get(side), evalCase, side, repeat);
                    if (shuttingDown) {
                        return null;
                    }
                    if (answered == null) {
                        return Ending.failed(EvalRunFailure.MODEL_UNAVAILABLE);
                    }
                    answers.put(key, answered);
                }
                if (rubric == null || judged.contains(pairKeyOf(evalCase.id(), repeat))) {
                    continue;
                }
                Ending stopped = stopBeforeCall(claimed);
                if (stopped != null) {
                    return stopped == Ending.NOT_HELD ? null : stopped;
                }
                boolean judgedNow = judge(run, rubric, judgeVersion, evalCase, repeat,
                        answers.get(keyOf(evalCase.id(), EvalRunSide.CANDIDATE, repeat)).answer(),
                        answers.get(keyOf(evalCase.id(), EvalRunSide.BASELINE, repeat)).answer());
                if (shuttingDown) {
                    return null;
                }
                if (!judgedNow) {
                    return Ending.failed(EvalRunFailure.MODEL_UNAVAILABLE);
                }
            }
        }
        List<EvalJudgement> judgements = rubric == null ? List.of() : rubrics.judgementsOf(run.id());
        // Whether the judged criteria count is read as the run finishes and stored with it: labelling later
        // changes no run.
        EvalRunJudged judgedSummary = rubric == null ? null : JudgedScorer.judgedOf(rubric,
                store.find(run.id()).orElse(run), calibration.standingOf(rubric), judgements);
        EvalRunSummary summary = EvalRunScorer.summarise(subject, cases, store.answersOf(run.id()), rubric,
                judgements, judgedSummary);
        return Ending.finished(summary, EvalRunScorer.verdictOf(subject, summary));
    }

    /** Null: the next call may be made. Asked before every call, a judge's among them. */
    private Ending stopBeforeCall(ClaimedEvalRun claimed) {
        EvalRunPulse pulse = store.pulse(claimed);
        if (!pulse.held()) {
            log.info("Eval run {} is no longer this worker's", claimed.run().number());
            return Ending.NOT_HELD;
        }
        if (pulse.stopRequested()) {
            return Ending.cancelled();
        }
        return pulse.callsMade() >= claimed.run().callCap() ? Ending.failed(EvalRunFailure.CALL_CAP_REACHED) : null;
    }

    /**
     * Null when the model answered nothing, asked {@link #ASKS_PER_ANSWER} times. Such a call is never stored as
     * an answer: both versions failing alike during an outage would otherwise score as no worse.
     */
    private EvalRunAnswer answer(EvalRun run, EvalSubject subject, ServedPrompt version, EvalRunCase evalCase,
                                 EvalRunSide side, int repeat) {
        for (int ask = 0; ask < ASKS_PER_ANSWER && !shuttingDown; ask++) {
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
     * when the judge answered nothing, asked {@link #ASKS_PER_ANSWER} times: an outage is never stored as a tie.
     */
    private boolean judge(EvalRun run, EvalRubric rubric, ServedPrompt judgeVersion, EvalRunCase evalCase,
                          int repeat, JsonNode candidate, JsonNode baseline) {
        boolean candidateFirst = ThreadLocalRandom.current().nextBoolean();
        for (int ask = 0; ask < ASKS_PER_ANSWER && !shuttingDown; ask++) {
            JudgeReading reading = judge.judge(judgeVersion, rubric.criteria(), evalCase.input(),
                    candidateFirst ? candidate : baseline, candidateFirst ? baseline : candidate);
            if (!reading.isAnswered()) {
                store.addJudgeSpend(run.id(), judgeVersion.version(), 1, 0, 0, null);
                continue;
            }
            List<EvalJudgement> judgements = new ArrayList<>();
            for (RubricCriterion criterion : rubric.criteria()) {
                JudgedPick pick = reading.picks().getOrDefault(criterion.key(), JudgedPick.UNREADABLE);
                judgements.add(new EvalJudgement(evalCase.id(), repeat, criterion.key(),
                        candidateFirst ? JudgePick.A : JudgePick.B, winnerOf(pick.pick(), candidateFirst),
                        pick.reason()));
            }
            PromptTrialResult asked = reading.asked();
            transactions.executeWithoutResult(status -> {
                rubrics.addJudgements(run.id(), judgements);
                store.addJudgeSpend(run.id(), judgeVersion.version(), asked.modelCalls(), asked.inputTokens(),
                        asked.outputTokens(), asked.model());
            });
            return true;
        }
        return false;
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
        if (ending == null) {
            return;
        }
        EvalRunVerdict verdict = ending.verdict();
        boolean ended = ending.status() == EvalRunStatus.FINISHED
                ? store.finish(claimed, verdict, ending.summary())
                : store.end(claimed, ending.status(), ending.failure());
        // A cancelled run was audited when the stop was asked for, by the person who asked.
        if (!ended || ending.status() == EvalRunStatus.CANCELLED) {
            return;
        }
        store.find(claimed.run().id()).ifPresent(run -> {
            AuditService.Builder event = audit.event(PlatformEventType.EVAL_RUN_FINISHED)
                    .actor(run.startedBy())
                    .target("eval_run", run.id())
                    .detail("number", run.number())
                    .detail("prompt", run.promptId())
                    .detail("status", run.status().name())
                    .detailIfPresent("verdict", verdict == null ? null : verdict.name())
                    .detailIfPresent("failure", ending.failure() == null ? null : ending.failure().name())
                    .detail("calls", run.callsMade())
                    .detail("tokens", run.inputTokens() + run.outputTokens());
            if (ending.summary() != null && ending.summary().judged() != null) {
                event.detail("judgedCounted", ending.summary().judged().counted());
            }
            if (ending.status() == EvalRunStatus.FAILED) {
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
        shuttingDown = true;
        thread.shutdownNow();
    }

    /** How a run this worker held came to an end. {@code verdict} is null for a finished run with no result. */
    private record Ending(EvalRunStatus status, EvalRunFailure failure, EvalRunSummary summary,
                          EvalRunVerdict verdict) {

        /** The run is another worker's now: nothing here ends it. */
        static final Ending NOT_HELD = new Ending(null, null, null, null);

        static Ending finished(EvalRunSummary summary, EvalRunVerdict verdict) {
            return new Ending(EvalRunStatus.FINISHED, null, summary, verdict);
        }

        static Ending cancelled() {
            return new Ending(EvalRunStatus.CANCELLED, null, null, null);
        }

        static Ending failed(EvalRunFailure failure) {
            return new Ending(EvalRunStatus.FAILED, failure, null, null);
        }
    }
}
