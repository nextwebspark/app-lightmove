package app.lightmove.api.triagecompany.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.CompanyListSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.strategy.constant.CompanySortField;
import app.lightmove.api.strategy.constant.SortDirection;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.strategy.service.StrategyService;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.AddTriageCompanyRequest;
import app.lightmove.api.triagecompany.dto.TriageBulkAddResponse;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.dto.TriageCountsDto;
import app.lightmove.api.triagecompany.dto.UpdateTriageCompanyRequest;
import app.lightmove.api.triagecompany.model.TriageCompany;
import app.lightmove.api.triagecompany.repository.TriageCompanyRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A mandate's triaged companies: taking one out of the market, moving it between the three stages,
 * and reading a stage back a page at a time.
 *
 * <p>Every row is a snapshot resolved from the market at write time — the client names an id and
 * nothing else, so it cannot file a company under a name of its own choosing, and the row keeps
 * rendering after the Apollo pipeline stops publishing its subject.
 */
@Service
public class TriageCompanyService {

    /** A scope, not an attack. */
    public static final int MAX_PAGE_SIZE = 100;

    private final TriageCompanyRepository triaged;
    private final ProjectRepository projects;
    private final StrategyService strategy;
    private final AuditService audit;
    private final ApolloCompanyQueryService market;
    private final CompanyListSettings listConfig;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public TriageCompanyService(TriageCompanyRepository triaged, ProjectRepository projects,
                                StrategyService strategy, AuditService audit,
                                ApolloCompanyQueryService market, LightMoveProperties properties) {
        this.triaged = triaged;
        this.projects = projects;
        this.strategy = strategy;
        this.audit = audit;
        this.market = market;
        this.listConfig = properties.company().list();
    }

    /**
     * One stage, newest first. The three counts travel with every page because the stage sub-nav is
     * always visible, and a badge that only refreshed on its own tab would be wrong on the tab you
     * were looking at.
     */
    @Transactional(readOnly = true)
    public TriageCompaniesResponse list(UUID workspaceId, UUID projectId, String statusToken,
                                        int page, int size) {
        if (page < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        TriageCompanyStatus status = resolveStatus(statusToken);
        requireProject(projectId, workspaceId);

        Page<TriageCompany> found = triaged.findByProjectIdAndStatus(projectId, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return new TriageCompaniesResponse(
                found.getContent().stream().map(TriageCompanyService::toDto).toList(),
                found.getTotalElements(), page, size,
                new TriageCountsDto(
                        triaged.countByProjectIdAndStatus(projectId, TriageCompanyStatus.IN_UNIVERSE),
                        triaged.countByProjectIdAndStatus(projectId, TriageCompanyStatus.SHORTLISTED),
                        triaged.countByProjectIdAndStatus(projectId, TriageCompanyStatus.DECLINED)));
    }

    @Transactional
    public TriageCompanyResponse add(UUID userId, UUID workspaceId, UUID projectId,
                                     AddTriageCompanyRequest request, HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        String accountId = request.apolloAccountId();

        // Already held is not an error: the button is on every row and a second click means the same
        // thing as the first. Returning the existing row makes the response idempotent.
        List<TriageCompany> existing =
                triaged.findByProjectIdAndApolloAccountIdIn(projectId, List.of(accountId));
        if (!existing.isEmpty()) {
            return toDto(existing.getFirst());
        }

        CompanyRow row = market.byAccountIds(List.of(accountId)).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Not in the universe: " + accountId));
        TriageCompany taken = triaged.save(snapshotOf(projectId, userId, row));

        audit.event(ProjectEventType.TRIAGE_COMPANY_ADDED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("apolloAccountId", accountId)
                .record();
        return toDto(taken);
    }

    /**
     * "Add all to Universe", capped. An untouched filter matches all 71,822 companies, so this takes
     * the first {@code bulkAddLimit} and <b>says</b> it capped. Companies the mandate already holds are
     * skipped, declined ones included: re-running after widening must not resurrect a ruled-out company.
     */
    @Transactional
    public TriageBulkAddResponse addAllInScope(UUID userId, UUID workspaceId, UUID projectId,
                                               HttpServletRequest httpRequest) {
        CompanyScope scope = strategy.scopeOf(workspaceId, projectId);
        int limit = listConfig.bulkAddLimit();
        boolean capped = market.count(scope) > limit;

        List<CompanyRow> rows = market.search(scope, CompanySortField.EMPLOYEES, SortDirection.DESC,
                0, limit);
        Set<String> alreadyHeld = new HashSet<>();
        triaged.findByProjectIdAndApolloAccountIdIn(projectId,
                        rows.stream().map(CompanyRow::apolloAccountId).toList())
                .forEach(held -> alreadyHeld.add(held.getApolloAccountId()));

        List<TriageCompany> taken = new ArrayList<>();
        for (CompanyRow row : rows) {
            if (alreadyHeld.contains(row.apolloAccountId())) {
                continue;
            }
            taken.add(snapshotOf(projectId, userId, row));
        }
        triaged.saveAll(taken);

        audit.event(ProjectEventType.TRIAGE_BULK_ADDED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("added", String.valueOf(taken.size()))
                .record();
        return new TriageBulkAddResponse(taken.size(), rows.size() - taken.size(), capped, limit);
    }

    @Transactional
    public TriageCompanyResponse update(UUID userId, UUID workspaceId, UUID projectId,
                                        UUID triageCompanyId, UpdateTriageCompanyRequest request,
                                        HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        TriageCompany company = triaged.findByIdAndProjectId(triageCompanyId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        // Null leaves that half alone. Moving a company to Declined must not silently clear the note
        // explaining why, and clearing a note is an explicit empty string rather than an omission.
        if (request.status() != null) {
            TriageCompanyStatus status = TriageCompanyStatus.fromValue(request.status());
            if (status == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown status: " + request.status());
            }
            company.moveTo(status);
        }
        if (request.note() != null) {
            company.annotate(request.note());
        }

        audit.event(ProjectEventType.TRIAGE_COMPANY_MOVED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("triageCompanyId", triageCompanyId.toString())
                .record();
        return toDto(company);
    }

    /** The landing stage is where a company arrives from Strategy. */
    private static TriageCompanyStatus resolveStatus(String token) {
        if (token == null || token.isBlank()) {
            return TriageCompanyStatus.IN_UNIVERSE;
        }
        TriageCompanyStatus status = TriageCompanyStatus.fromValue(token);
        if (status == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown status: " + token);
        }
        return status;
    }

    private void requireProject(UUID projectId, UUID workspaceId) {
        projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private static TriageCompany snapshotOf(UUID projectId, UUID userId, CompanyRow row) {
        return TriageCompany.taken(projectId, userId, row.apolloAccountId(), row.companyName(),
                row.industry(), row.companyCountry(), row.companyCity(), row.numEmployees(),
                row.annualRevenue(), row.website(), row.logoUrl());
    }

    static TriageCompanyResponse toDto(TriageCompany company) {
        return new TriageCompanyResponse(company.getId(), company.getApolloAccountId(),
                company.getStatus().value(), company.getNote(), company.getCompanyName(),
                company.getIndustry(), company.getCompanyCountry(), company.getCompanyCity(),
                company.getNumEmployees(), company.getAnnualRevenue(), company.getWebsite(),
                company.getLogoUrl());
    }
}
