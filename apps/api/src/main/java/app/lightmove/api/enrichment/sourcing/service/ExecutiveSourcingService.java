package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.service.FairUseGuard;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.ExecutiveSourcingSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.stream.ProjectStreamKind;
import app.lightmove.api.core.stream.ProjectStreamPublisher;
import app.lightmove.api.enrichment.sourcing.constant.SourcingRunStatus;
import app.lightmove.api.enrichment.sourcing.dto.ExecutiveSourcingConfigResponse;
import app.lightmove.api.enrichment.sourcing.dto.ExecutiveSourcingRunResponse;
import app.lightmove.api.enrichment.sourcing.dto.StartExecutiveSourcingRequest;
import app.lightmove.api.enrichment.sourcing.model.ExecutiveSourcingRequested;
import app.lightmove.api.enrichment.sourcing.model.ExecutiveSourcingRun;
import app.lightmove.api.enrichment.sourcing.model.SearchCriteria;
import app.lightmove.api.enrichment.sourcing.model.SourcingCompany;
import app.lightmove.api.enrichment.sourcing.repository.ExecutiveSourcingRunRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The request side of Find executives: decides which companies a search takes, refuses what it must, checks fair
 * use against the profiles it may buy, writes the run with the position's saved search and queues it; stops one.
 * The reads answer the panel's poll. {@code @Transactional} because the worker listens {@code AFTER_COMMIT}.
 */
@Service
public class ExecutiveSourcingService {

    private final ExecutiveSourcingRunRepository runs;
    private final SourcingCompanies companies;
    private final SourcingCriteriaService criteria;
    private final CompanySearch companySearch;
    private final FairUseGuard fairUse;
    private final AuditService audit;
    private final ProjectStreamPublisher stream;
    private final ApplicationEventPublisher events;
    private final ExecutiveSourcingSettings settings;

    public ExecutiveSourcingService(ExecutiveSourcingRunRepository runs, SourcingCompanies companies,
                                    SourcingCriteriaService criteria, CompanySearch companySearch,
                                    FairUseGuard fairUse, AuditService audit, ProjectStreamPublisher stream,
                                    ApplicationEventPublisher events, LightMoveProperties properties) {
        this.runs = runs;
        this.companies = companies;
        this.criteria = criteria;
        this.companySearch = companySearch;
        this.fairUse = fairUse;
        this.audit = audit;
        this.stream = stream;
        this.events = events;
        this.settings = properties.enrichment().sourcing();
    }

    public ExecutiveSourcingConfigResponse config() {
        return new ExecutiveSourcingConfigResponse(companySearch.isOffered(), settings.maxCompaniesPerRun(),
                SearchCriteria.PER_COMPANY_CHOICES, SearchCriteria.DEFAULT_PER_COMPANY,
                settings.lostAfter().toSeconds());
    }

    @Transactional
    public ExecutiveSourcingRunResponse request(UUID userId, UUID workspaceId, UUID projectId,
                                                StartExecutiveSourcingRequest request,
                                                HttpServletRequest httpRequest) {
        if (!companySearch.isOffered()) {
            throw ApiException.of(ErrorCode.EXECUTIVE_SOURCING_UNAVAILABLE);
        }
        List<SourcingCompany> chosen = companies.chosen(workspaceId, projectId, request.triageCompanyIds(),
                settings.maxCompaniesPerRun());
        refuseRunInProgress(workspaceId, projectId);
        if (chosen.isEmpty()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "triageCompanyIds",
                    "Every In-universe company already has an executive mapped");
        }
        SearchCriteria searched = criteria.current(workspaceId, projectId);
        if (!searched.words().isSearchable()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "criteria",
                    "Add a title word to search for");
        }
        fairUse.check(workspaceId, userId, UsageKind.SOURCING_RUN, (long) chosen.size() * searched.perCompany());
        ExecutiveSourcingRun run = saveRefusingASecond(ExecutiveSourcingRun.requested(workspaceId, projectId, userId,
                chosen, searched));
        stream.publish(projectId, ProjectStreamKind.EXECUTIVE_SOURCING);
        audit.projectEvent(ProjectEventType.EXECUTIVE_SOURCING_REQUESTED, userId, workspaceId, projectId, httpRequest)
                .detail("runId", run.getId().toString())
                .detail("companies", chosen.size())
                .detail("perCompany", searched.perCompany())
                .detail("selected", request.triageCompanyIds() != null && !request.triageCompanyIds().isEmpty())
                .record();
        events.publishEvent(new ExecutiveSourcingRequested(run.getId(), projectId, workspaceId, userId));
        return ExecutiveSourcingRunResponse.of(run);
    }

    /** Asks a run in progress to stop: the companies already searching finish, the rest are not searched. */
    @Transactional
    public ExecutiveSourcingRunResponse stop(UUID workspaceId, UUID projectId, UUID runId) {
        ExecutiveSourcingRun run = runs.findByIdAndWorkspaceIdAndProjectId(runId, workspaceId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (run.requestStop()) {
            stream.publish(projectId, ProjectStreamKind.EXECUTIVE_SOURCING);
        }
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
}
