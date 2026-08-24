package app.lightmove.api.triagecompany.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.CompanyListSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.text.service.LinkedInCompanySlug;
import app.lightmove.api.core.text.service.WebsiteDomain;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.strategy.constant.CompanySortField;
import app.lightmove.api.strategy.constant.SortDirection;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.strategy.service.StrategyService;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.AddTriageCompanyRequest;
import app.lightmove.api.triagecompany.dto.CaptureCompanyRequest;
import app.lightmove.api.triagecompany.dto.TriageBulkAddResponse;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.dto.TriageCountsDto;
import app.lightmove.api.triagecompany.dto.UpdateTriageCompanyRequest;
import app.lightmove.api.triagecompany.model.TriageCompany;
import app.lightmove.api.triagecompany.model.TriageCompanySnapshot;
import app.lightmove.api.triagecompany.repository.TriageCompanyRepository;
import app.lightmove.api.triagecompany.repository.TriageCompanyWriter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
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

    /**
     * The two stages a capture may land in. Declined is deliberately not among them: ruling a company
     * out is a triage decision taken with the mandate in view, not something a browser popup does.
     */
    private static final Set<TriageCompanyStatus> CAPTURE_DESTINATIONS =
            EnumSet.of(TriageCompanyStatus.IN_UNIVERSE, TriageCompanyStatus.SHORTLISTED);

    private final TriageCompanyRepository triaged;
    private final TriageCompanyWriter writer;
    private final ProjectRepository projects;
    private final StrategyService strategy;
    private final AuditService audit;
    private final ApolloCompanyQueryService market;
    private final CompanyListSettings listConfig;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public TriageCompanyService(TriageCompanyRepository triaged, TriageCompanyWriter writer,
                                ProjectRepository projects, StrategyService strategy,
                                AuditService audit, ApolloCompanyQueryService market,
                                LightMoveProperties properties) {
        this.triaged = triaged;
        this.writer = writer;
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
                                        Integer requestedPage, Integer requestedSize) {
        int page = requestedPage == null ? 0 : requestedPage;
        int size = requestedSize == null ? listConfig.defaultPageSize() : requestedSize;
        if (page < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must not be negative");
        }
        if (size < 1 || size > listConfig.maxPageSize()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "size must be between 1 and " + listConfig.maxPageSize());
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
        Optional<TriageCompany> held = triaged.findByProjectIdAndApolloAccountId(projectId, accountId);
        if (held.isPresent()) {
            return toDto(held.get());
        }

        CompanyScope scope = strategy.scopeOf(workspaceId, projectId);
        if (scope.offLimitsAccountIds().contains(accountId)) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "This company is off-limits for this mandate.");
        }

        CompanyRow row = market.byAccountIds(List.of(accountId)).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Not in the universe: " + accountId));

        // The check above is a fast path, not the guard: a second click racing this one passes it too.
        // The insert ignores the conflict and the row is read back either way, so both callers get the
        // company and only the one that actually wrote it records an event.
        int inserted = writer.insertIgnoringHeld(projectId, userId, List.of(row));
        TriageCompany taken = triaged.findByProjectIdAndApolloAccountId(projectId, accountId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        if (inserted > 0) {
            audit.event(ProjectEventType.TRIAGE_COMPANY_ADDED)
                    .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                    .detail("apolloAccountId", accountId)
                    .record();
        }
        return toDto(taken);
    }

    /**
     * A company written in from the browser extension.
     *
     * <p>The difference from {@link #add} is which company it can accept, not what it writes. That one
     * names an Apollo id and the universe answers what the company is. This one starts from a page,
     * which the universe may never have heard of — so it <b>resolves first and falls back second</b>:
     *
     * <ul>
     *   <li>Resolved to a universe row — by the id the caller sent, or failing that by the page's
     *       domain and LinkedIn slug — and the row is written exactly as Strategy would write it:
     *       snapshot from Apollo, request's company fields ignored, off-limits enforced. The rule that
     *       a client cannot file a known company under a name of its own choosing is untouched.
     *   <li>Not resolved, and the row is keyed on the normalised domain and carries what the page said.
     *       The off-limits list is keyed to Apollo ids, so it cannot speak to a company Apollo does not
     *       publish; nothing is silently skipped here, there is simply nothing to compare against.
     * </ul>
     *
     * <p>A company the mandate already holds is <b>promoted, never demoted</b>: capturing to the
     * shortlist moves a company sitting in the universe, capturing to the universe leaves a shortlisted
     * one alone. Both answer with the row, so a second click means the same as the first. A declined
     * company is refused outright rather than quietly revived — see {@link ErrorCode#TRIAGE_COMPANY_DECLINED}.
     */
    @Transactional
    public TriageCompanyResponse capture(UUID userId, UUID workspaceId, UUID projectId,
                                         CaptureCompanyRequest request, HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        TriageCompanyStatus destination = resolveCaptureDestination(request.status());

        Optional<CompanyRow> universeMatch = resolveAgainstUniverse(request);
        TriageCompany held = universeMatch
                .flatMap(row -> triaged.findByProjectIdAndApolloAccountId(projectId, row.apolloAccountId()))
                .or(() -> Optional.ofNullable(captureKeyOf(request))
                        .flatMap(key -> triaged.findByProjectIdAndCaptureKey(projectId, key)))
                .orElse(null);

        if (held != null) {
            return annotateAndPromote(held, destination, request);
        }
        TriageCompany taken = universeMatch.isPresent()
                ? fromUniverse(workspaceId, projectId, userId, universeMatch.get(), destination, request)
                : fromPage(projectId, userId, destination, request);

        taken.annotate(request.note());
        taken.retag(request.tags());
        triaged.save(taken);

        audit.event(ProjectEventType.TRIAGE_COMPANY_CAPTURED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("origin", taken.getOrigin().name())
                .detail("company", universeMatch.map(CompanyRow::apolloAccountId).orElse(taken.getCaptureKey()))
                .record();
        return toDto(taken);
    }

    /**
     * The id the caller sent wins, because the extension resolved it against the same universe a moment
     * ago and a match by id is exact. Only when it sent none is the page's own web identity tried.
     */
    private Optional<CompanyRow> resolveAgainstUniverse(CaptureCompanyRequest request) {
        if (request.apolloAccountId() != null && !request.apolloAccountId().isBlank()) {
            return market.byAccountIds(List.of(request.apolloAccountId())).stream().findFirst();
        }
        return market.byDomainOrLinkedIn(WebsiteDomain.of(request.website()),
                LinkedInCompanySlug.of(request.linkedinUrl()));
    }

    private TriageCompany fromUniverse(UUID workspaceId, UUID projectId, UUID userId, CompanyRow row,
                                       TriageCompanyStatus destination, CaptureCompanyRequest request) {
        CompanyScope scope = strategy.scopeOf(workspaceId, projectId);
        if (scope.offLimitsAccountIds().contains(row.apolloAccountId())) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "This company is off-limits for this mandate.");
        }
        return TriageCompany.fromUniverse(projectId, userId, row.apolloAccountId(), destination,
                snapshotOf(row), request.sourceUrl());
    }

    private TriageCompany fromPage(UUID projectId, UUID userId, TriageCompanyStatus destination,
                                   CaptureCompanyRequest request) {
        String captureKey = captureKeyOf(request);
        if (captureKey == null) {
            // Without a domain there is nothing to key the row on, and two captures of the same
            // company would become two rows. The popup requires a website for exactly this reason.
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "A website is required for a company that is not in the universe.");
        }
        return TriageCompany.fromPage(projectId, userId, captureKey, destination,
                new TriageCompanySnapshot(request.companyName().trim(), request.industry(),
                        request.companyCountry(), request.companyCity(), request.numEmployees(),
                        request.annualRevenue(), request.website(), request.linkedinUrl(), null),
                request.sourceUrl());
    }

    /**
     * Already held. Promotion only, and the note and tags are applied because a re-capture is usually
     * someone adding what they meant to say the first time.
     */
    private TriageCompanyResponse annotateAndPromote(TriageCompany held, TriageCompanyStatus destination,
                                                     CaptureCompanyRequest request) {
        if (held.getStatus() == TriageCompanyStatus.DECLINED) {
            throw ApiException.of(ErrorCode.TRIAGE_COMPANY_DECLINED);
        }
        if (destination == TriageCompanyStatus.SHORTLISTED) {
            held.moveTo(TriageCompanyStatus.SHORTLISTED);
        }
        held.annotate(request.note());
        held.retag(request.tags());
        return toDto(held);
    }

    private static String captureKeyOf(CaptureCompanyRequest request) {
        String fromWebsite = WebsiteDomain.of(request.website());
        return fromWebsite != null ? fromWebsite : WebsiteDomain.of(request.sourceUrl());
    }

    private static TriageCompanySnapshot snapshotOf(CompanyRow row) {
        return new TriageCompanySnapshot(row.companyName(), row.industry(), row.companyCountry(),
                row.companyCity(), row.numEmployees(), row.annualRevenue(), row.website(),
                row.companyLinkedinUrl(), row.logoUrl());
    }

    private static TriageCompanyStatus resolveCaptureDestination(String token) {
        TriageCompanyStatus status = TriageCompanyStatus.fromValue(token);
        if (status == null || !CAPTURE_DESTINATIONS.contains(status)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "A capture goes to the universe or the shortlist, not: " + token);
        }
        return status;
    }

    /**
     * "Add all to Universe". A filter matching more than {@code bulkAddLimit} is <b>refused whole</b>:
     * an untouched one matches all 71,822 companies, and taking the first {@code bulkAddLimit} of them
     * would silently decide which ones a mandate got. Companies the mandate already holds are skipped,
     * declined ones included: re-running after widening must not resurrect a ruled-out company.
     */
    @Transactional
    public TriageBulkAddResponse addAllInScope(UUID userId, UUID workspaceId, UUID projectId,
                                               HttpServletRequest httpRequest) {
        CompanyScope scope = strategy.scopeOf(workspaceId, projectId);
        int limit = listConfig.bulkAddLimit();
        long matching = market.count(scope);
        if (matching > limit) {
            // Interpolated into a user-facing message, which the class doc otherwise reserves for
            // literals. Neither number came from the caller: the scope is the mandate's stored filter
            // — this endpoint takes no body — and the limit is configuration.
            throw ApiException.userFacing(ErrorCode.BULK_ADD_SCOPE_TOO_LARGE,
                    "%,d companies match this filter. You can add %,d at a time — narrow it and try again."
                            .formatted(matching, limit));
        }

        List<CompanyRow> rows = market.search(scope, CompanySortField.EMPLOYEES, SortDirection.DESC,
                0, limit);

        // No read-then-filter: the insert ignores the companies the mandate already holds, so the
        // count it answers with is the number that were new. A row already declined stays declined —
        // re-running after widening the filter must not resurrect a ruled-out company.
        int added = writer.insertIgnoringHeld(projectId, userId, rows);

        audit.event(ProjectEventType.TRIAGE_BULK_ADDED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("added", String.valueOf(added))
                .record();
        return new TriageBulkAddResponse(added, rows.size() - added);
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


    private static TriageCompanyResponse toDto(TriageCompany company) {
        return new TriageCompanyResponse(company.getId(), company.getApolloAccountId(),
                company.getStatus().value(), company.getNote(), company.getCompanyName(),
                company.getIndustry(), company.getCompanyCountry(), company.getCompanyCity(),
                company.getNumEmployees(), company.getAnnualRevenue(), company.getWebsite(),
                company.getLinkedinUrl(), company.getLogoUrl(), company.getOrigin().name(),
                company.getSourceUrl(), company.getTags());
    }
}
