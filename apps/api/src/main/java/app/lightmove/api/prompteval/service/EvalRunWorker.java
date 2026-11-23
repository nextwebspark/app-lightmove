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
import app.lightmove.api.prompteval.dto.EvalRunSummary;
import app.lightmove.api.prompteval.model.ClaimedEvalRun;
import app.lightmove.api.prompteval.model.EvalRun;
import app.lightmove.api.prompteval.model.EvalRunAnswer;
import app.lightmove.api.prompteval.model.EvalRunCase;
import app.lightmove.api.prompteval.model.EvalRunPulse;
import app.lightmove.api.prompteval.model.EvalRunQueued;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Answers a run's cases with both versions, off every request: woken when a start commits, and by a timer for a run
 * no start handed over (a restart, a worker gone quiet).
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
    private final EvalSubjects subjects;
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

    EvalRunWorker(EvalRunStore store, EvalSubjects subjects, PromptRegistryStore registry, PromptTrialRunner runner,
                  TransactionTemplate transactions, AuditService audit, Clock clock, LightMoveProperties properties) {
        this.store = store;
        this.subjects = subjects;
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
                Optional<ClaimedEvalRun> claimed = claimNext();
                while (claimed.isPresent()) {
                    work(claimed.get());
                    claimed = claimNext();
                }
            } catch (RuntimeException failure) {
                log.error("The eval worker could not look for a run; the timer looks again", failure);
            } finally {
                working.set(false);
            }
            lookAgain = isOneWaiting() && working.compareAndSet(false, true);
        }
    }

    /** A run given up on ends here rather than in {@link #end}, and is audited as any other ending is. */
    private Optional<ClaimedEvalRun> claimNext() {
        for (UUID givenUp : store.giveUpOnOverclaimed(staleBefore())) {
            store.find(givenUp).ifPresent(this::auditEnded);
        }
        return store.claim(staleBefore());
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
        List<EvalRunCase> cases = store.casesOf(run.id());
        Set<AnswerSlot> answered = new HashSet<>();
        store.answersOf(run.id()).forEach(answer ->
                answered.add(new AnswerSlot(answer.caseId(), answer.side(), answer.repeat())));

        for (EvalRunCase evalCase : cases) {
            for (int repeat = 0; repeat < run.repeats(); repeat++) {
                for (EvalRunSide side : EvalRunSide.values()) {
                    if (answered.contains(new AnswerSlot(evalCase.id(), side, repeat))) {
                        continue;
                    }
                    AskOutcome outcome = answer(claimed, subject, versions.get(side), evalCase, side, repeat);
                    if (shuttingDown) {
                        return null;
                    }
                    switch (outcome) {
                        case ANSWERED -> { }
                        case CLAIM_LOST -> {
                            log.info("Eval run {} is no longer this worker's", run.number());
                            return null;
                        }
                        case STOP_REQUESTED -> {
                            return Ending.cancelled();
                        }
                        case CALL_CAP_REACHED -> {
                            return Ending.failed(EvalRunFailure.CALL_CAP_REACHED);
                        }
                        case MODEL_SILENT -> {
                            return Ending.failed(EvalRunFailure.MODEL_UNAVAILABLE);
                        }
                    }
                }
            }
        }
        EvalRunSummary summary = EvalRunScorer.summarise(subject, cases, store.answersOf(run.id()));
        return Ending.finished(summary);
    }

    /**
     * {@code MODEL_SILENT} when the model answered nothing, asked {@link #ASKS_PER_ANSWER} times. Such a call is
     * never stored as an answer: both versions failing alike during an outage would otherwise score as no worse.
     *
     * <p>The run's pulse is raised before every ask, not once an answer: three slow asks would otherwise outlast
     * {@code stale-after} and hand a run still being worked to another instance.
     */
    private AskOutcome answer(ClaimedEvalRun claimed, EvalSubject subject, ServedPrompt version,
                              EvalRunCase evalCase, EvalRunSide side, int repeat) {
        EvalRun run = claimed.run();
        for (int ask = 0; ask < ASKS_PER_ANSWER && !shuttingDown; ask++) {
            EvalRunPulse pulse = store.pulse(claimed);
            if (!pulse.held()) {
                return AskOutcome.CLAIM_LOST;
            }
            if (pulse.stopRequested()) {
                return AskOutcome.STOP_REQUESTED;
            }
            if (pulse.callsMade() >= run.callCap()) {
                return AskOutcome.CALL_CAP_REACHED;
            }
            PromptTrialResult asked = null;
            try {
                asked = runner.run(version, () -> subject.answer(evalCase.input()));
            } catch (RuntimeException unanswered) {
                // The exception's own text is left out: a transport failure can quote what was sent.
                log.warn("Eval run {} case {} got no answer: {}", run.number(), evalCase.key(),
                        unanswered.getClass().getSimpleName());
            }
            if (asked == null || asked.modelCalls() == 0) {
                if (!store.addSpend(claimed, side, 1, 0, 0, null)) {
                    return AskOutcome.CLAIM_LOST;
                }
                continue;
            }
            EvalRunAnswer answer = new EvalRunAnswer(evalCase.id(), side, repeat, asked.parsedAnswer(),
                    verdictsOn(subject, evalCase, asked));
            PromptTrialResult stored = asked;
            // The spend first: it takes the run's row, so the claim cannot change hands before the answer lands.
            boolean kept = Boolean.TRUE.equals(transactions.execute(status -> {
                if (!store.addSpend(claimed, side, stored.modelCalls(), stored.inputTokens(),
                        stored.outputTokens(), stored.model())) {
                    return false;
                }
                store.addAnswer(claimed, answer, stored);
                return true;
            }));
            return kept ? AskOutcome.ANSWERED : AskOutcome.CLAIM_LOST;
        }
        return AskOutcome.MODEL_SILENT;
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
        EvalRunVerdict verdict = ending.summary() == null ? null
                : EvalRunScorer.isNoWorse(ending.summary()) ? EvalRunVerdict.PASS : EvalRunVerdict.FAIL;
        boolean ended = ending.status() == EvalRunStatus.FINISHED
                ? store.finish(claimed, verdict, ending.summary())
                : store.end(claimed, ending.status(), ending.failure());
        // A cancelled run was audited when the stop was asked for, by the person who asked.
        if (!ended || ending.status() == EvalRunStatus.CANCELLED) {
            return;
        }
        store.find(claimed.run().id()).ifPresent(this::auditEnded);
    }

    private void auditEnded(EvalRun run) {
        AuditService.Builder event = audit.event(PlatformEventType.EVAL_RUN_FINISHED)
                .actor(run.startedBy())
                .target("eval_run", run.id())
                .detail("number", run.number())
                .detail("prompt", run.promptId())
                .detail("status", run.status().name())
                .detailIfPresent("verdict", run.verdict() == null ? null : run.verdict().name())
                .detailIfPresent("failure", run.failure() == null ? null : run.failure().name())
                .detail("calls", run.callsMade())
                .detail("tokens", run.inputTokens() + run.outputTokens());
        if (run.status() == EvalRunStatus.FAILED) {
            event.failed();
        }
        event.record();
    }

    private ServedPrompt versionOf(EvalRun run, int version) {
        return registry.version(run.promptId(), version)
                .orElseThrow(() -> new IllegalStateException(
                        "No version " + version + " of prompt " + run.promptId()));
    }

    private Instant staleBefore() {
        return clock.instant().minus(settings.staleAfter());
    }

    @PreDestroy
    void stop() {
        shuttingDown = true;
        thread.shutdownNow();
    }

    private enum AskOutcome { ANSWERED, MODEL_SILENT, CLAIM_LOST, STOP_REQUESTED, CALL_CAP_REACHED }

    private record AnswerSlot(UUID caseId, EvalRunSide side, int repeat) {}

    /** How a run this worker held came to an end. */
    private record Ending(EvalRunStatus status, EvalRunFailure failure, EvalRunSummary summary) {

        static Ending finished(EvalRunSummary summary) {
            return new Ending(EvalRunStatus.FINISHED, null, summary);
        }

        static Ending cancelled() {
            return new Ending(EvalRunStatus.CANCELLED, null, null);
        }

        static Ending failed(EvalRunFailure failure) {
            return new Ending(EvalRunStatus.FAILED, failure, null);
        }
    }
}
