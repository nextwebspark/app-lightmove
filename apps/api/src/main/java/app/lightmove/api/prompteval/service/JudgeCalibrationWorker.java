package app.lightmove.api.prompteval.service;

import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PlatformEvalSettings;
import app.lightmove.api.core.llm.model.ServedPrompt;
import app.lightmove.api.core.llm.service.PromptRegistryStore;
import app.lightmove.api.prompteval.constant.CalibrationCheckStatus;
import app.lightmove.api.prompteval.model.CalibrationCheck;
import app.lightmove.api.prompteval.model.CalibrationCheckQueued;
import app.lightmove.api.prompteval.model.CalibrationPair;
import app.lightmove.api.prompteval.model.CalibrationPick;
import app.lightmove.api.prompteval.model.ClaimedCalibrationCheck;
import app.lightmove.api.prompteval.model.EvalRubric;
import app.lightmove.api.prompteval.model.JudgeReading;
import app.lightmove.api.prompteval.model.JudgedPick;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Checks the judge against a rubric's labels, off every request: it makes its own picks on the labelled pairs, each
 * shown in a random order, and the check ends with how many matched. One thread of its own
 * ({@link ClaimedJobDrainer}), like {@link EvalRunWorker}'s.
 */
@Slf4j
@Component
public class JudgeCalibrationWorker {

    private final JudgeCalibrationStore store;
    private final EvalRubricStore rubrics;
    private final EvalJudge judge;
    private final PromptRegistryStore registry;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final Clock clock;
    private final PlatformEvalSettings settings;
    private final ClaimedJobDrainer<ClaimedCalibrationCheck> drainer;

    JudgeCalibrationWorker(JudgeCalibrationStore store, EvalRubricStore rubrics, EvalJudge judge,
                           PromptRegistryStore registry, TransactionTemplate transactions, AuditService audit,
                           Clock clock, LightMoveProperties properties) {
        this.store = store;
        this.rubrics = rubrics;
        this.judge = judge;
        this.registry = registry;
        this.transactions = transactions;
        this.audit = audit;
        this.clock = clock;
        this.settings = properties.platform().eval();
        this.drainer = new ClaimedJobDrainer<>("eval-judge-check", () -> store.claim(staleBefore()), this::work,
                () -> store.isOneWaiting(staleBefore()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void queued(CalibrationCheckQueued queued) {
        wake();
    }

    @Scheduled(fixedDelayString = "${lightmove.platform.eval.worker-tick}",
            initialDelayString = "${lightmove.platform.eval.worker-tick}")
    public void wake() {
        drainer.wake();
    }

    /** No check is being worked here: what a test waits for. */
    public boolean isIdle() {
        return drainer.isIdle();
    }

    private void work(ClaimedCalibrationCheck claimed) {
        Boolean judgedAll;
        try {
            judgedAll = judgeAll(claimed);
        } catch (RuntimeException failure) {
            if (drainer.isShuttingDown()) {
                return;
            }
            log.error("The judge check {} stopped unexpectedly", claimed.check().id(), failure);
            judgedAll = false;
        }
        if (judgedAll == null) {
            return;
        }
        boolean ended = judgedAll ? store.finish(claimed) : store.fail(claimed);
        if (ended) {
            store.findCheck(claimed.check().id()).ifPresent(this::record);
        }
    }

    /**
     * Null: the check is no longer this worker's to end. False: the model gave no answer to a pair, asked
     * several times over, so the check fails rather than counting an outage as disagreement.
     */
    private Boolean judgeAll(ClaimedCalibrationCheck claimed) {
        CalibrationCheck check = claimed.check();
        EvalRubric rubric = rubrics.find(check.rubricId())
                .orElseThrow(() -> new IllegalStateException("No rubric " + check.rubricId()));
        ServedPrompt version = registry.version(EvalJudge.PROMPT_ID, check.judgeVersion())
                .orElseThrow(() -> new IllegalStateException("No version " + check.judgeVersion()
                        + " of the eval judge"));
        Set<UUID> judged = store.picksOf(check.id()).stream().map(CalibrationPick::pairId)
                .collect(Collectors.toSet());
        for (CalibrationPair pair : store.pairsOf(rubric.id())) {
            if (judged.contains(pair.id())) {
                continue;
            }
            if (!store.pulse(claimed)) {
                log.info("The judge check {} is no longer this worker's", check.id());
                return null;
            }
            boolean answered = judge(check, version, rubric, pair);
            if (drainer.isShuttingDown()) {
                return null;
            }
            if (!answered) {
                return false;
            }
        }
        return true;
    }

    private boolean judge(CalibrationCheck check, ServedPrompt version, EvalRubric rubric, CalibrationPair pair) {
        boolean aShownFirst = ThreadLocalRandom.current().nextBoolean();
        Optional<JudgeReading> answered = judge.judgeUntilAnswered(version, rubric.criteria(), pair.input(),
                aShownFirst ? pair.answerA() : pair.answerB(), aShownFirst ? pair.answerB() : pair.answerA(),
                drainer::isShuttingDown, unanswered -> store.addSpend(check.id(), unanswered));
        if (answered.isEmpty()) {
            return false;
        }
        JudgeReading reading = answered.get();
        List<CalibrationPick> picks = rubric.criteria().stream()
                .map(criterion -> {
                    JudgedPick shown = reading.picks().getOrDefault(criterion.key(), JudgedPick.UNREADABLE);
                    return new CalibrationPick(pair.id(), criterion.key(),
                            shown.pick() == null || aShownFirst ? shown.pick() : shown.pick().swapped(),
                            shown.reason());
                })
                .toList();
        transactions.executeWithoutResult(status -> {
            store.addPicks(check.id(), aShownFirst, picks);
            store.addSpend(check.id(), reading);
        });
        return true;
    }

    private void record(CalibrationCheck ended) {
        AuditService.Builder event = audit.event(PlatformEventType.EVAL_JUDGE_CHECK_FINISHED)
                .actor(ended.startedBy())
                .target("eval_calibration_check", ended.id())
                .detail("rubric", ended.rubricId())
                .detail("judgeVersion", ended.judgeVersion())
                .detail("status", ended.status().name())
                .detailIfPresent("agreed", ended.agreed())
                .detailIfPresent("picks", ended.picks());
        if (ended.status() == CalibrationCheckStatus.FAILED) {
            event.failed();
        }
        event.record();
    }

    private Instant staleBefore() {
        return clock.instant().minus(settings.staleAfter());
    }

    @PreDestroy
    void stop() {
        drainer.stop();
    }
}
