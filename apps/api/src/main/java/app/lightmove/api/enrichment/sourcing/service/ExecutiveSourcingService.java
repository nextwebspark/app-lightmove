package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.ExecutiveSourcingSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.core.stream.ProjectStreamKind;
import app.lightmove.api.core.stream.ProjectStreamPublisher;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.core.text.service.WebsiteDomain;
import app.lightmove.api.enrichment.sourcing.constant.SourcingRunStatus;
import app.lightmove.api.enrichment.sourcing.dto.ExecutiveSourcingConfigResponse;
import app.lightmove.api.enrichment.sourcing.dto.ExecutiveSourcingRunResponse;
import app.lightmove.api.enrichment.sourcing.dto.StartExecutiveSourcingRequest;
import app.lightmove.api.enrichment.sourcing.model.ExecutiveSourcingRequested;
import app.lightmove.api.enrichment.sourcing.model.ExecutiveSourcingRun;
import app.lightmove.api.enrichment.sourcing.model.SourcingCompany;
import app.lightmove.api.enrichment.sourcing.repository.ExecutiveSourcingRunRepository;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The request side of Find executives: decides which companies a run takes, refuses what it must,
 * spends the budget, writes the run and queues it. The reads answer the screen's poll.
 * {@code @Transactional} because the worker listens {@code AFTER_COMMIT}.
 */
@Service
public class ExecutiveSourcingService {

    private final ExecutiveSourcingRunRepository runs;
    private final TriageCompanyReadService companies;
    private final CandidateService candidates;
    private final PeopleSearch peopleSearch;
    private final LlmBudgetGuard llmBudget;
    private final AuditService audit;
    private final ProjectStreamPublisher stream;
    private final ApplicationEventPublisher events;
    private final ExecutiveSourcingSettings settings;

    public ExecutiveSourcingService(ExecutiveSourcingRunRepository runs, TriageCompanyReadService companies,
                                    CandidateService candidates, PeopleSearch peopleSearch,
                                    LlmBudgetGuard llmBudget, AuditService audit, ProjectStreamPublisher stream,
                                    ApplicationEventPublisher events, LightMoveProperties properties) {
        this.runs = runs;
        this.companies = companies;
        this.candidates = candidates;
        this.peopleSearch = peopleSearch;
        this.llmBudget = llmBudget;
        this.audit = audit;
        this.stream = stream;
        this.events = events;
        this.settings = properties.enrichment().sourcing();
    }

    public ExecutiveSourcingConfigResponse config() {
        return new ExecutiveSourcingConfigResponse(peopleSearch.isOffered(), settings.maxCompaniesPerRun(),
                settings.hitsPerCompany(), settings.picksPerCompany(), settings.lostAfter().toSeconds());
    }

    /**
     * No ids: the first {@code maxCompaniesPerRun} In-universe companies with nobody mapped, in the
     * grid's own name order. Ids: exactly those, each of which must be In universe here.
     */
    @Transactional
    public ExecutiveSourcingRunResponse request(UUID userId, UUID workspaceId, UUID projectId,
                                                StartExecutiveSourcingRequest request,
                                                HttpServletRequest httpRequest) {
        if (!peopleSearch.isOffered()) {
            throw ApiException.of(ErrorCode.EXECUTIVE_SOURCING_UNAVAILABLE);
        }
        List<UUID> asked = request.triageCompanyIds() == null ? List.of()
                : List.copyOf(new LinkedHashSet<>(request.triageCompanyIds()));
        int cap = settings.maxCompaniesPerRun();
        if (asked.size() > cap) {
            throw ApiException.of(ErrorCode.EXECUTIVE_SOURCING_TOO_MANY_COMPANIES);
        }
        refuseRunInProgress(workspaceId, projectId);

        List<SourcingCompany> chosen = asked.isEmpty()
                ? firstWithoutExecutive(workspaceId, projectId, cap)
                : named(workspaceId, projectId, asked);
        if (chosen.isEmpty()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "triageCompanyIds",
                    "Every In-universe company already has an executive mapped");
        }

        llmBudget.require(LlmBudget.EXECUTIVE_SOURCING, userId);
        ExecutiveSourcingRun run = saveRefusingASecond(ExecutiveSourcingRun.requested(workspaceId, projectId, userId,
                chosen));
        stream.publish(projectId, ProjectStreamKind.EXECUTIVE_SOURCING);
        audit.projectEvent(ProjectEventType.EXECUTIVE_SOURCING_REQUESTED, userId, workspaceId, projectId, httpRequest)
                .detail("runId", run.getId().toString())
                .detail("companies", chosen.size())
                .detail("selected", !asked.isEmpty())
                .record();
        events.publishEvent(new ExecutiveSourcingRequested(run.getId(), projectId, workspaceId, userId));
        return ExecutiveSourcingRunResponse.of(run);
    }

    @Transactional(readOnly = true)
    public ExecutiveSourcingRunResponse statusOf(UUID workspaceId, UUID projectId, UUID runId) {
        return runs.findByIdAndWorkspaceIdAndProjectId(runId, workspaceId, projectId)
                .map(ExecutiveSourcingRunResponse::of)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Optional<ExecutiveSourcingRunResponse> latestOf(UUID workspaceId, UUID projectId) {
        return runs.findFirstByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(workspaceId, projectId)
                .map(ExecutiveSourcingRunResponse::of);
    }

    /** A run lost with its instance is failed here, so it never holds the mandate's one slot for good. */
    private void refuseRunInProgress(UUID workspaceId, UUID projectId) {
        Instant cutoff = Instant.now().minus(settings.lostAfter());
        for (ExecutiveSourcingRun inProgress : runs.findByWorkspaceIdAndProjectIdAndStatusIn(workspaceId, projectId,
                SourcingRunStatus.IN_PROGRESS)) {
            if (!inProgress.isLostBefore(cutoff)) {
                throw ApiException.of(ErrorCode.EXECUTIVE_SOURCING_IN_PROGRESS);
            }
            inProgress.fail("Uncava lost track of this run");
            runs.flush();
        }
    }

    /** V88's partial unique index settles two requests racing past {@link #refuseRunInProgress}. */
    private ExecutiveSourcingRun saveRefusingASecond(ExecutiveSourcingRun run) {
        try {
            return runs.saveAndFlush(run);
        } catch (DataIntegrityViolationException raced) {
            throw ApiException.of(ErrorCode.EXECUTIVE_SOURCING_IN_PROGRESS);
        }
    }

    /** The grid's name order, skipping companies with an executive mapped or marked as having none. */
    private List<SourcingCompany> firstWithoutExecutive(UUID workspaceId, UUID projectId, int cap) {
        return companies.firstOfStageExcluding(workspaceId, projectId, TriageCompanyStatus.IN_UNIVERSE,
                        candidates.companiesWithExecutivesOf(workspaceId, projectId), cap).stream()
                .map(ExecutiveSourcingService::toSourcingCompany)
                .toList();
    }

    private List<SourcingCompany> named(UUID workspaceId, UUID projectId, List<UUID> asked) {
        Map<UUID, TriageCompanyResponse> byId = companies.findOfStage(workspaceId, projectId,
                        TriageCompanyStatus.IN_UNIVERSE, Set.copyOf(asked)).stream()
                .collect(Collectors.toMap(TriageCompanyResponse::id, Function.identity()));
        if (byId.size() < asked.size()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "triageCompanyIds",
                    "Only In-universe companies of this position can be searched");
        }
        return asked.stream().map(byId::get).map(ExecutiveSourcingService::toSourcingCompany).toList();
    }

    private static SourcingCompany toSourcingCompany(TriageCompanyResponse company) {
        return new SourcingCompany(company.id(), company.companyName(),
                LinkedInUrls.companySlugOrNull(company.companyLinkedinUrl()), WebsiteDomain.of(company.website()),
                company.numEmployees());
    }
}
