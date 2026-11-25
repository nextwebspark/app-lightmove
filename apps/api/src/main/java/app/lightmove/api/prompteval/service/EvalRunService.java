package app.lightmove.api.prompteval.service;

import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PlatformEvalSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.llm.model.ProductionVersion;
import app.lightmove.api.core.llm.model.ServedPrompt;
import app.lightmove.api.core.llm.service.EvalSubject;
import app.lightmove.api.core.llm.service.PromptRegistryStore;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import app.lightmove.api.prompteval.constant.EvalRunStatus;
import app.lightmove.api.prompteval.dto.EvalRunCaseComparison;
import app.lightmove.api.prompteval.dto.EvalRunCases;
import app.lightmove.api.prompteval.dto.EvalRunDetail;
import app.lightmove.api.prompteval.dto.EvalRunEstimate;
import app.lightmove.api.prompteval.dto.EvalRunOverview;
import app.lightmove.api.prompteval.dto.EvalRunSideDetail;
import app.lightmove.api.prompteval.dto.StartEvalRunRequest;
import app.lightmove.api.prompteval.model.EvalCallAverages;
import app.lightmove.api.prompteval.model.EvalRubric;
import app.lightmove.api.prompteval.model.EvalRun;
import app.lightmove.api.prompteval.model.EvalRunCase;
import app.lightmove.api.prompteval.model.EvalRunPlan;
import app.lightmove.api.prompteval.model.EvalRunQueued;
import app.lightmove.api.prompteval.model.NewEvalRun;
import app.lightmove.api.promptregistry.constant.PlatformPrompt;
import app.lightmove.api.promptregistry.service.PromptCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Eval runs as the platform console starts, watches and stops them. A start stores a queued run and returns; the
 * answering, and the judging where the dataset has a rubric, is {@link EvalRunWorker}'s. One run goes at a time,
 * platform-wide.
 */
@Service
public class EvalRunService {

    static final int LISTED = 50;

    /** For a prompt no run has measured: about four characters to a token, and an answer of a few hundred. */
    private static final double CHARACTERS_PER_TOKEN = 4;
    private static final double UNMEASURED_ANSWER_TOKENS = 400;
    private static final double UNMEASURED_CALL_MILLIS = 5_000;

    private final EvalRunStore store;
    private final EvalRunPlanner planner;
    private final EvalSubjects subjects;
    private final EvalRubricStore rubrics;
    private final PromptCatalogService catalog;
    private final PromptRegistryStore registry;
    private final LlmBudgetGuard budget;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final AuditService audit;
    private final Clock clock;
    private final PlatformEvalSettings settings;

    EvalRunService(EvalRunStore store, EvalRunPlanner planner, EvalSubjects subjects, EvalRubricStore rubrics,
                   PromptCatalogService catalog, PromptRegistryStore registry, LlmBudgetGuard budget,
                   TransactionTemplate transactions, ApplicationEventPublisher events, AuditService audit,
                   Clock clock, LightMoveProperties properties) {
        this.store = store;
        this.planner = planner;
        this.subjects = subjects;
        this.rubrics = rubrics;
        this.catalog = catalog;
        this.registry = registry;
        this.budget = budget;
        this.transactions = transactions;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
        this.settings = properties.platform().eval();
    }

    @Transactional(readOnly = true)
    public List<EvalRunOverview> list() {
        return store.latest(LISTED).stream().map(EvalRunService::overviewOf).toList();
    }

    @Transactional(readOnly = true)
    public EvalRunDetail get(UUID runId) {
        return detailOf(require(runId));
    }

    @Transactional(readOnly = true)
    public EvalRunEstimate estimate(StartEvalRunRequest request) {
        EvalRunPlan plan = planner.plan(request);
        EvalCallAverages measured = store.averagesOf(plan.dataset().promptId());
        double tokensPerCall = measured.tokens() != null ? measured.tokens()
                : (plan.candidate().body().length() + plan.baseline().body().length()) / 2.0 / CHARACTERS_PER_TOKEN
                        + store.meanInputLengthOf(plan.dataset().id(), plan.split()) / CHARACTERS_PER_TOKEN
                        + UNMEASURED_ANSWER_TOKENS;
        double millisPerCall = measured.millis() != null ? measured.millis() : UNMEASURED_CALL_MILLIS;
        return new EvalRunEstimate(plan.baseline().version(), plan.cases(), plan.repeats(), plan.repeatsReason(),
                plan.calls(), plan.judgeCalls(), plan.judgedCounts(),
                Math.round(tokensPerCall * plan.calls()),
                (int) Math.ceil(millisPerCall * plan.calls() / Duration.ofMinutes(1).toMillis()),
                settings.maxCallsPerRun(), plan.calls() > settings.maxCallsPerRun());
    }

    public EvalRunDetail start(UUID userId, StartEvalRunRequest request, HttpServletRequest httpRequest) {
        EvalRunPlan plan = planner.plan(request);
        if (plan.cases() == 0) {
            throw ApiException.of(ErrorCode.EVAL_NO_CASES);
        }
        if (plan.calls() > settings.maxCallsPerRun()) {
            throw ApiException.of(ErrorCode.EVAL_RUN_TOO_LARGE);
        }
        // Asked before the budget is spent: a start refused for a run already going costs nothing.
        if (store.isOneQueuedOrRunning()) {
            throw ApiException.of(ErrorCode.EVAL_RUN_IN_PROGRESS);
        }
        budget.require(LlmBudget.EVAL_RUN, userId);

        NewEvalRun queued = new NewEvalRun(plan.dataset().promptId(), plan.dataset().id(), plan.split(),
                plan.candidate().version(), plan.baseline().version(), plan.repeats(), plan.subject().isGateReady(),
                settings.maxCallsPerRun(), userId, plan.rubric() == null ? null : plan.rubric().id());
        UUID runId;
        try {
            runId = transactions.execute(status -> {
                UUID id = store.queue(queued).orElseThrow(() -> ApiException.of(ErrorCode.EVAL_NO_CASES));
                // The run's cases are counted as it is stored: one imported since the plan can take it over the cap.
                if (require(id).callsPlanned() > settings.maxCallsPerRun()) {
                    throw ApiException.of(ErrorCode.EVAL_RUN_TOO_LARGE);
                }
                events.publishEvent(new EvalRunQueued(id));
                return id;
            });
        } catch (DuplicateKeyException racing) {
            // Two starts landing together: V239's partial unique index holds one run at a time.
            throw ApiException.of(ErrorCode.EVAL_RUN_IN_PROGRESS);
        }
        EvalRun run = require(runId);
        audit.event(PlatformEventType.EVAL_RUN_STARTED).actor(userId)
                .target("eval_run", runId)
                .detail("number", run.number())
                .detail("prompt", run.promptId())
                .detail("dataset", run.datasetId())
                .detail("split", run.split().name())
                .detail("candidateVersion", run.candidateVersion())
                .detail("baselineVersion", run.baselineVersion())
                .detail("callsPlanned", run.callsPlanned())
                .from(httpRequest)
                .record();
        return detailOf(run);
    }

    /** Cancelling a run that has ended, or twice, is the first ending: the run reads the same either way. */
    public EvalRunDetail cancel(UUID userId, UUID runId, HttpServletRequest httpRequest) {
        EvalRun run = require(runId);
        if (store.requestStop(runId)) {
            audit.event(PlatformEventType.EVAL_RUN_CANCELLED).actor(userId)
                    .target("eval_run", runId)
                    .detail("number", run.number())
                    .detail("prompt", run.promptId())
                    .detail("calls", run.callsMade())
                    .from(httpRequest)
                    .record();
        }
        return detailOf(require(runId));
    }

    /** A run that gave no result has no comparison to read, and neither has one whose prompt can no longer be run. */
    @Transactional(readOnly = true)
    public EvalRunCases cases(UUID runId) {
        EvalRun run = require(runId);
        Optional<EvalSubject> subject = subjects.of(run.promptId());
        if (run.status() != EvalRunStatus.FINISHED || subject.isEmpty()) {
            return new EvalRunCases(List.of(), 0);
        }
        List<EvalRunCase> cases = store.casesOf(runId);
        EvalRubric rubric = run.rubricId() == null ? null : rubrics.find(run.rubricId()).orElse(null);
        List<EvalRunCaseComparison> changed = EvalRunScorer.changedCases(subject.get(), cases,
                store.answersOf(runId), rubric, rubric == null ? List.of() : rubrics.judgementsOf(runId));
        return new EvalRunCases(changed, cases.size() - changed.size());
    }

    private EvalRun require(UUID runId) {
        return store.find(runId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No eval run " + runId));
    }

    private EvalRunDetail detailOf(EvalRun run) {
        return new EvalRunDetail(overviewOf(run),
                sideOf(run, run.candidateVersion(), run.candidateModel()),
                sideOf(run, run.baselineVersion(), run.baselineModel()),
                run.gateReady() && run.split() == EvalCaseSplit.HOLDOUT && run.status() == EvalRunStatus.FINISHED,
                run.callCap(), secondsLeftOf(run), run.summary());
    }

    private EvalRunSideDetail sideOf(EvalRun run, int version, String answeredBy) {
        Optional<PlatformPrompt> prompt = PlatformPrompt.of(run.promptId());
        Optional<ServedPrompt> served = registry.version(run.promptId(), version);
        Integer live = registry.productionOf(run.promptId()).map(ProductionVersion::version).orElse(null);
        String model = answeredBy != null ? answeredBy
                : served.map(ServedPrompt::modelId)
                        .or(() -> prompt.map(catalog::defaultModelOf))
                        .orElse(null);
        Double temperature = prompt.isPresent() && served.isPresent()
                ? planner.temperatureOf(prompt.get(), served.get()) : null;
        return new EvalRunSideDetail(version, live != null && live == version, model, temperature);
    }

    /** From the pace so far. Null unless the run is going and has made a call to judge the pace by. */
    private Long secondsLeftOf(EvalRun run) {
        if (run.status() != EvalRunStatus.RUNNING || run.startedAt() == null || run.callsMade() == 0) {
            return null;
        }
        long elapsed = Duration.between(run.startedAt(), clock.instant()).toSeconds();
        return Math.max(0, elapsed * Math.max(0, run.callsPlanned() - run.callsMade()) / run.callsMade());
    }

    private static EvalRunOverview overviewOf(EvalRun run) {
        return new EvalRunOverview(run.id(), run.number(), run.promptId(),
                PlatformPrompt.of(run.promptId()).map(PlatformPrompt::getDisplayName).orElse(run.promptId()),
                run.datasetId(), run.datasetName(), run.split(), run.candidateVersion(), run.baselineVersion(),
                run.status(), run.verdict(), run.failure(), run.rubricId() != null, run.caseCount(), run.repeats(),
                run.callsPlanned(),
                run.callsMade(), run.inputTokens(), run.outputTokens(), run.startedByName(), run.createdAt(),
                run.startedAt(), run.finishedAt());
    }
}
