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
import app.lightmove.api.prompteval.dto.EvalRunJudged;
import app.lightmove.api.prompteval.dto.EvalRunSummary;
import app.lightmove.api.prompteval.model.ClaimedEvalRun;
import app.lightmove.api.prompteval.model.EvalJudgement;
import app.lightmove.api.prompteval.model.EvalRubric;
import app.lightmove.api.prompteval.model.EvalRun;
import app.lightmove.api.prompteval.model.EvalRunAnswer;
import app.lightmove.api.prompteval.model.EvalRunCase;
import app.lightmove.api.prompteval.model.EvalRunPulse;
import app.lightmove.api.prompteval.model.EvalRunQueued;
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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
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
        this.drainer = new ClaimedJobDrainer<>("eval-run", this::claimNext, this::work,
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

    /** A run given up on ends here rather than in {@link #end}, and is audited as any other ending is. */
    private Optional<ClaimedEvalRun> claimNext() {
        for (UUID givenUp : store.giveUpOnOverclaimed(staleBefore())) {
            store.find(givenUp).ifPresent(this::auditEnded);
        }
        return store.claim(staleBefore());
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
        Map<AnswerSlot, EvalRunAnswer> answers = new HashMap<>();
        store.answersOf(run.id()).forEach(answer ->
                answers.put(new AnswerSlot(answer.caseId(), answer.side(), answer.repeat()), answer));
        Set<JudgedPair> judged = new HashSet<>();
        if (rubric != null) {
            rubrics.judgementsOf(run.id()).forEach(judgement ->
                    judged.add(new JudgedPair(judgement.caseId(), judgement.repeat())));
        }

        for (EvalRunCase evalCase : cases) {
            for (int repeat = 0; repeat < run.repeats(); repeat++) {
                for (EvalRunSide side : EvalRunSide.values()) {
                    if (answers.containsKey(new AnswerSlot(evalCase.id(), side, repeat))) {
                        continue;
                    }
                    Optional<Ending> stopped = answer(claimed, subject, versions.get(side), evalCase, side, repeat,
                            answers);
                    if (drainer.isShuttingDown()) {
                        return new Ending.Left();
                    }
                    if (stopped.isPresent()) {
                        return stopped.get();
                    }
                }
                if (rubric == null || judged.contains(new JudgedPair(evalCase.id(), repeat))) {
                    continue;
                }
                Optional<Ending> stopped = judge(claimed, rubric, judgeVersion, evalCase, repeat,
                        answers.get(new AnswerSlot(evalCase.id(), EvalRunSide.CANDIDATE, repeat)).answer(),
                        answers.get(new AnswerSlot(evalCase.id(), EvalRunSide.BASELINE, repeat)).answer());
                if (drainer.isShuttingDown()) {
                    return new Ending.Left();
                }
                if (stopped.isPresent()) {
                    return stopped.get();
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

    /**
     * Empty: the next call may be made. Asked before every ask, a judge's among them, and raises the run's pulse:
     * three slow asks behind one pulse would otherwise outlast {@code stale-after} and hand a run still being
     * worked to another instance.
     */
    private Optional<Ending> stopBeforeCall(ClaimedEvalRun claimed) {
        EvalRunPulse pulse = store.pulse(claimed);
        if (!pulse.held()) {
            return Optional.of(left(claimed));
        }
        if (pulse.stopRequested()) {
            return Optional.of(new Ending.Cancelled());
        }
        return pulse.callsMade() >= claimed.run().callCap()
                ? Optional.of(new Ending.Failed(EvalRunFailure.CALL_CAP_REACHED)) : Optional.empty();
    }

    private Ending left(ClaimedEvalRun claimed) {
        log.info("Eval run {} is no longer this worker's", claimed.run().number());
        return new Ending.Left();
    }

    /**
     * Empty once the answer is stored and added to {@code answers}. The model answering nothing, asked
     * {@link #ASKS_PER_ANSWER} times, fails the run: such a call is never stored as an answer, since both versions
     * failing alike during an outage would otherwise score as no worse.
     */
    private Optional<Ending> answer(ClaimedEvalRun claimed, EvalSubject subject, ServedPrompt version,
                                    EvalRunCase evalCase, EvalRunSide side, int repeat,
                                    Map<AnswerSlot, EvalRunAnswer> answers) {
        EvalRun run = claimed.run();
        for (int ask = 0; ask < ASKS_PER_ANSWER && !drainer.isShuttingDown(); ask++) {
            Optional<Ending> stopped = stopBeforeCall(claimed);
            if (stopped.isPresent()) {
                return stopped;
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
                    return Optional.of(left(claimed));
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
            if (!kept) {
                return Optional.of(left(claimed));
            }
            answers.put(new AnswerSlot(evalCase.id(), side, repeat), answer);
            return Optional.empty();
        }
        return Optional.of(new Ending.Failed(EvalRunFailure.MODEL_UNAVAILABLE));
    }

    /**
     * Has the judge compare one pair, the two answers in an order drawn at random and stored with its picks. Empty
     * once they are stored. A judge that never answered fails the run: an outage is never stored as a tie.
     */
    private Optional<Ending> judge(ClaimedEvalRun claimed, EvalRubric rubric, ServedPrompt judgeVersion,
                                   EvalRunCase evalCase, int repeat, JsonNode candidate, JsonNode baseline) {
        boolean candidateFirst = ThreadLocalRandom.current().nextBoolean();
        AtomicReference<Ending> stopped = new AtomicReference<>();
        Optional<JudgeReading> answered = judge.judgeUntilAnswered(judgeVersion, rubric.criteria(), evalCase.input(),
                candidateFirst ? candidate : baseline, candidateFirst ? baseline : candidate,
                () -> {
                    if (drainer.isShuttingDown() || stopped.get() != null) {
                        return true;
                    }
                    stopBeforeCall(claimed).ifPresent(stopped::set);
                    return stopped.get() != null;
                },
                unanswered -> {
                    if (!store.addJudgeSpend(claimed, unanswered)) {
                        stopped.set(left(claimed));
                    }
                });
        if (stopped.get() != null) {
            return Optional.of(stopped.get());
        }
        if (answered.isEmpty()) {
            return Optional.of(new Ending.Failed(EvalRunFailure.MODEL_UNAVAILABLE));
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
        // The spend first, as for an answer: a run taken over while the judge read stores no picks from this worker.
        boolean kept = Boolean.TRUE.equals(transactions.execute(status -> {
            if (!store.addJudgeSpend(claimed, reading)) {
                return false;
            }
            rubrics.addJudgements(claimed.run().id(), judgements);
            return true;
        }));
        return kept ? Optional.empty() : Optional.of(left(claimed));
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
                    store.find(claimed.run().id()).ifPresent(this::auditEnded);
                }
            }
            case Ending.Finished finished -> {
                if (store.finish(claimed, finished.verdict(), finished.summary())) {
                    store.find(claimed.run().id()).ifPresent(this::auditEnded);
                }
            }
        }
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
        if (run.summary() != null && run.summary().judged() != null) {
            event.detail("judgedCounted", run.summary().judged().counted());
        }
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
        drainer.stop();
    }

    private record AnswerSlot(UUID caseId, EvalRunSide side, int repeat) {}

    private record JudgedPair(UUID caseId, int repeat) {}

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
