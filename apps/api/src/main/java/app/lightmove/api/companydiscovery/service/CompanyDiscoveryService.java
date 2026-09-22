package app.lightmove.api.companydiscovery.service;

import app.lightmove.api.companydiscovery.dto.DiscoverCompaniesRequest;
import app.lightmove.api.companydiscovery.dto.DiscoveredCompanyDto;
import app.lightmove.api.companydiscovery.dto.DiscoveryConfigResponse;
import app.lightmove.api.companydiscovery.dto.DiscoveryResponse;
import app.lightmove.api.companydiscovery.model.DiscoveryAnswer;
import app.lightmove.api.companydiscovery.model.DiscoveryQuery;
import app.lightmove.api.companydiscovery.model.HeldCompanies;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.CompanyDiscoverySettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.core.ratelimit.service.WorkspaceDailySpend;
import app.lightmove.api.core.ratelimit.service.WorkspaceSpendMeter;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.strategy.service.CompanySearchLimits;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * One AI Research search: what it is allowed to cost, what it asks, and what it may answer.
 *
 * <p><b>Deliberately not {@code @Transactional}.</b> A grounded model call and up to twenty-five
 * scans of the universe sit in the middle of this, and holding one of five pool connections across
 * them would take the application down long before the bill did.
 *
 * <p>The order below is the interesting part. Nothing is spent before the deployment is known to
 * offer discovery at all — {@code CompanyResearch} makes the same check before touching its cache,
 * and for the same reason: silence from a provider nobody configured is not a finding, and charging
 * a workspace's day for it is charging for nothing.
 */
@Service
public class CompanyDiscoveryService {

    private final CompanyDiscovery discovery;
    private final CandidateResolver resolver;
    private final MandateCompanyReader mandates;
    private final ProjectAccess projectAccess;
    private final LlmBudgetGuard llmBudget;
    private final WorkspaceDailySpend dailySpend;
    private final AuditService audit;
    private final CompanyDiscoverySettings settings;

    public CompanyDiscoveryService(CompanyDiscovery discovery, CandidateResolver resolver,
                                   MandateCompanyReader mandates, ProjectAccess projectAccess,
                                   LlmBudgetGuard llmBudget, WorkspaceDailySpend dailySpend,
                                   AuditService audit, LightMoveProperties properties) {
        this.discovery = discovery;
        this.resolver = resolver;
        this.mandates = mandates;
        this.projectAccess = projectAccess;
        this.llmBudget = llmBudget;
        this.dailySpend = dailySpend;
        this.audit = audit;
        this.settings = properties.company().discovery();
    }

    public DiscoveryConfigResponse config() {
        return new DiscoveryConfigResponse(discovery.isEnabled(),
                settings.dailySearchesPerWorkspace());
    }

    public DiscoveryResponse discover(UUID userId, UUID workspaceId,
                                      DiscoverCompaniesRequest request,
                                      HttpServletRequest httpRequest) {
        String question = CompanySearchLimits.accepted("question", request.question(),
                settings.maxQuestionLength());
        int limit = CompanySearchLimits.resolved(request.limit(), settings.defaultResultLimit(),
                settings.maxResultLimit());

        // Naming a mandate authorises nothing on its own. ProjectAccess 404s a project outside the
        // caller's workspace before it reaches the admin bypass, so "another firm's mandate is not
        // visible" holds here without a second check of its own.
        if (request.projectId() != null) {
            projectAccess.requireAction(userId, workspaceId, request.projectId(),
                    ProjectAction.WORK_VIEW);
        }

        if (!discovery.isEnabled()) {
            throw ApiException.of(ErrorCode.COMPANY_DISCOVERY_UNAVAILABLE);
        }

        llmBudget.require(LlmBudget.COMPANY_DISCOVERY, userId);
        int spentToday = claimTodaysSearch(userId, workspaceId, request.projectId(), httpRequest);

        DiscoveryAnswer answer = discovery.discover(new DiscoveryQuery(question,
                request.country(), limit));
        HeldCompanies held = request.projectId() == null
                ? HeldCompanies.none()
                : mandates.heldBy(request.projectId());
        List<DiscoveredCompanyDto> rows = resolver.resolve(answer.candidates(), held);

        recordSearch(userId, workspaceId, request.projectId(), httpRequest, answer, rows, spentToday);

        return new DiscoveryResponse(rows, answer.mode().name(), discovery.provider(),
                Math.max(0, settings.dailySearchesPerWorkspace() - spentToday));
    }

    /**
     * Claims the day's budget before the provider is asked, which is the deliberate asymmetry: an
     * outage after this point burns a search. A compensating refund is the obvious alternative and is
     * worse — a second write that can itself fail or race, whose failure modes (double spend, a
     * negative counter) are worse than the one it removes. The default is sized for it.
     */
    private int claimTodaysSearch(UUID userId, UUID workspaceId, UUID projectId,
                                  HttpServletRequest httpRequest) {
        Optional<Integer> claimed = dailySpend.spend(WorkspaceSpendMeter.COMPANY_DISCOVERY,
                workspaceId, settings.dailySearchesPerWorkspace());
        if (claimed.isEmpty()) {
            AuditService.Builder refused = audit.event(WorkspaceEventType.COMPANY_DISCOVERY_RAN)
                    .actor(userId).workspace(workspaceId)
                    .from(httpRequest)
                    .detail("limit", String.valueOf(settings.dailySearchesPerWorkspace()))
                    .failed().reason("daily_cap");
            named(refused, projectId).record();
            throw ApiException.of(ErrorCode.COMPANY_DISCOVERY_DAILY_LIMIT_REACHED);
        }
        return claimed.get();
    }

    /**
     * Audited like {@code COMPANIES_EXPORTED} rather than like an ordinary read: this one spends the
     * firm's money and brings externally-sourced names into the product, and both of those are facts
     * somebody may need to reconstruct later.
     *
     * <p>The question itself is deliberately not a detail. It is the consultant's own research
     * thinking, and the audit trail is not where that belongs.
     */
    private void recordSearch(UUID userId, UUID workspaceId, UUID projectId,
                              HttpServletRequest httpRequest, DiscoveryAnswer answer,
                              List<DiscoveredCompanyDto> rows, int spentToday) {
        long unresolved = rows.stream().filter(DiscoveredCompanyDto::unresolved).count();
        AuditService.Builder ran = audit.event(WorkspaceEventType.COMPANY_DISCOVERY_RAN)
                .actor(userId).workspace(workspaceId)
                .from(httpRequest)
                .detail("mode", answer.mode().name())
                .detail("provider", discovery.provider())
                .detail("proposed", String.valueOf(answer.candidates().size()))
                .detail("rows", String.valueOf(rows.size()))
                .detail("unresolved", String.valueOf(unresolved))
                .detail("spentToday", String.valueOf(spentToday));
        named(ran, projectId).record();
    }

    /**
     * A search is workspace-level and may name no mandate, so the target is conditional rather than
     * a {@code project} row with a null id — which would read in the ledger as a mandate somebody
     * failed to record.
     */
    private static AuditService.Builder named(AuditService.Builder event, UUID projectId) {
        return projectId == null ? event : event.target("project", projectId);
    }
}
