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
import app.lightmove.api.triagecompany.constant.TriageCompanySortField;
import app.lightmove.api.triagecompany.constant.TriageCompanySource;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.AddTriageCompanyRequest;
import app.lightmove.api.triagecompany.dto.CaptureCompanyRequest;
import app.lightmove.api.triagecompany.dto.TriageBulkAddResponse;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyListCriteria;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.dto.TriageCountsDto;
import app.lightmove.api.triagecompany.dto.UpdateTriageCompanyRequest;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import app.lightmove.api.triagecompany.model.TriageCompany;
import app.lightmove.api.triagecompany.repository.TriageCompanyRepository;
import app.lightmove.api.triagecompany.repository.TriageCompanyWriter;
import jakarta.servlet.http.HttpServletRequest;
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
 * A mandate's triaged companies: taking one out of the market, capturing one the market does not
 * carry, moving it between the three stages, and removing it altogether.
 *
 * <p>A company taken from Strategy is a snapshot resolved from the market at write time — the client
 * names an id and nothing else, so it cannot file a company under a name of its own choosing, and the
 * row keeps rendering after the Apollo pipeline stops publishing its subject. A company the mandate
 * supplies itself has no id to resolve against, so the caller carries the fields and
 * {@link TriageCompanySource} records that they were not Apollo's.
 */
@Service
public class TriageCompanyService {

    /** The two doors a caller may supply a company through. {@code STRATEGY} is the server's to write. */
    private static final Set<TriageCompanySource> CAPTURABLE_SOURCES =
            Set.of(TriageCompanySource.MANUAL, TriageCompanySource.EXTENSION);

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

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
     * One stage, ordered and narrowed as the grid asks. The three counts travel with every page
     * because the stage switcher is always visible, and a badge that only refreshed on its own tab
     * would be wrong on the tab you were looking at — which is exactly the tab a move was made from.
     */
    @Transactional(readOnly = true)
    public TriageCompaniesResponse list(UUID workspaceId, UUID projectId,
                                        TriageCompanyListCriteria criteria) {
        int page = criteria.page() == null ? 0 : criteria.page();
        int size = criteria.size() == null ? listConfig.defaultPageSize() : criteria.size();
        if (page < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must not be negative");
        }
        if (size < 1 || size > listConfig.maxPageSize()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "size must be between 1 and " + listConfig.maxPageSize());
        }
        TriageCompanyStatus status = resolveStatus(criteria.status());
        requireProject(projectId, workspaceId);

        PageRequest pageRequest = PageRequest.of(page, size, resolveSort(criteria));
        String nameQuery = criteria.nameQuery() == null ? "" : criteria.nameQuery().trim();
        Page<TriageCompany> found = nameQuery.isEmpty()
                ? triaged.findByProjectIdAndStatus(projectId, status, pageRequest)
                : triaged.findByProjectIdAndStatusAndCompanyNameContainingIgnoreCase(
                        projectId, status, nameQuery, pageRequest);

        return new TriageCompaniesResponse(
                found.getContent().stream().map(TriageCompanyService::toDto).toList(),
                found.getTotalElements(), page, size, countsFor(projectId));
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
     * A company the mandate supplies itself — typed in on the Companies screen, or read off a live page
     * by the plugin. The market is never consulted: there is nothing to consult it about, which is the
     * entire reason this path exists.
     *
     * <p>Refused if the mandate already holds that name under <i>any</i> source. That is wider than the
     * partial unique index V34 adds, which can only see the manual rows, and it is the question a
     * consultant is actually asking — a company already taken from Apollo is "already there" whether or
     * not it arrived the same way.
     *
     * <p><b>The guard is deliberately one-directional</b>, and that is a product decision rather than an
     * oversight: a capture will not duplicate a name the mandate holds, but a later bulk add from
     * Strategy still will, because the alternatives are worse. Skipping the Apollo row would leave the
     * mandate with the thin hand-typed one and silently withhold the richer market record it matched;
     * merging the two is a real feature with a real UI, not something a bulk insert should decide. So
     * two same-named rows from different doors are possible, they are distinguishable by their Source
     * badge, and either can be removed.
     */
    @Transactional
    public TriageCompanyResponse capture(UUID userId, UUID workspaceId, UUID projectId,
                                         CaptureCompanyRequest request, HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);

        TriageCompanySource source = resolveCapturableSource(request.source());
        TriageCompanyStatus status = resolveStatus(request.status());
        CapturedCompanyDetails details = new CapturedCompanyDetails(
                request.companyName(), request.industry(), request.companyCountry(),
                request.companyCity(), request.numEmployees(), request.annualRevenue(),
                request.website(), request.companyLinkedinUrl(), request.foundedYear(),
                request.shortDescription(), request.sourceUrl(), request.note());

        if (triaged.existsByProjectIdAndCompanyNameIgnoreCase(projectId, details.companyName())) {
            throw ApiException.of(ErrorCode.TRIAGE_COMPANY_ALREADY_HELD);
        }

        TriageCompany captured = triaged.save(
                TriageCompany.captured(projectId, userId, source, status, details));

        audit.event(ProjectEventType.TRIAGE_COMPANY_CAPTURED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("source", source.name())
                .detail("triageCompanyId", captured.getId().toString())
                .record();
        return toDto(captured);
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

    /**
     * Drops this mandate's decision about a company. <b>Nothing of the company itself is deleted</b>:
     * {@code app_lm_apollo_companies} is ETL-owned and read-only to this application, so the company
     * stays in the universe, stays findable on Strategy, and stays untouched for every other mandate.
     * What goes is the one project↔company row — the mapping and the stage it had reached.
     *
     * <p>Unlike Declining, this is not remembered. A later "Add all to Universe" over a filter that
     * matches the company may take it back in as In universe, which is the accepted trade for a delete
     * that leaves nothing behind: to rule a company out durably, decline it.
     */
    @Transactional
    public void removeFromProject(UUID userId, UUID workspaceId, UUID projectId, UUID triageCompanyId,
                                  HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        TriageCompany company = triaged.findByIdAndProjectId(triageCompanyId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        triaged.delete(company);

        // The name is recorded because the row that carried it is about to stop existing, and an audit
        // entry naming only an id nobody can resolve answers no question later.
        audit.event(ProjectEventType.TRIAGE_COMPANY_REMOVED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("triageCompanyId", triageCompanyId.toString())
                .detail("companyName", company.getCompanyName())
                .record();
    }

    private TriageCountsDto countsFor(UUID projectId) {
        return new TriageCountsDto(
                triaged.countByProjectIdAndStatus(projectId, TriageCompanyStatus.IN_UNIVERSE),
                triaged.countByProjectIdAndStatus(projectId, TriageCompanyStatus.SHORTLISTED),
                triaged.countByProjectIdAndStatus(projectId, TriageCompanyStatus.DECLINED));
    }

    /**
     * Newest first unless the grid asked otherwise.
     *
     * <p>{@code NULLS LAST} regardless of direction, for the same reason {@code CompanySortField}
     * spells out: a missing figure is a data gap, not a small one, and a page of blanks is never what
     * "sort by revenue" was asking for. Apollo publishes a revenue figure on about one row in ten, and
     * those blanks travel into the snapshot, so without this an ascending revenue sort opens on the
     * very rows the ordering means to bury.
     *
     * <p>The secondary sort on {@code createdAt} keeps paging stable: the snapshot columns are full of
     * ties — a whole page can share one country — and Postgres is free to order tied rows differently
     * per query, which shuffles rows across page boundaries.
     */
    private static Sort resolveSort(TriageCompanyListCriteria criteria) {
        // Both tokens are resolved before either is used, so a bad direction is a 400 whether or not a
        // field came with it. Returning the default early instead would have let ?direction=sideways
        // through with a 200 — and silently ignored a well-formed ?direction=asc on its own.
        SortDirection direction = resolveDirection(criteria.direction());
        TriageCompanySortField field = resolveSortField(criteria.sort());
        if (field == null) {
            // No field named: the default ordering, which the caller may still have reversed.
            return newestFirstIn(direction == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC);
        }
        Sort.Order order = Sort.Order
                .by(field.property())
                .with(direction == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC)
                .nullsLast();
        // "Added" is createdAt itself, so tie-breaking on it again would be the same term twice.
        return field == TriageCompanySortField.ADDED ? Sort.by(order) : Sort.by(order).and(NEWEST_FIRST);
    }

    /** Null when the caller named no field, which is not the same as naming an unknown one. */
    private static TriageCompanySortField resolveSortField(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        TriageCompanySortField field = TriageCompanySortField.fromValue(token);
        if (field == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown sort field: " + token);
        }
        return field;
    }

    private static Sort newestFirstIn(Sort.Direction direction) {
        return direction == Sort.Direction.DESC ? NEWEST_FIRST : Sort.by(Sort.Direction.ASC, "createdAt");
    }

    /** Omitted means DESC: the grid opens newest-first, and an absent direction must not reverse it. */
    private static SortDirection resolveDirection(String token) {
        if (token == null || token.isBlank()) {
            return SortDirection.DESC;
        }
        SortDirection direction = SortDirection.fromValue(token);
        if (direction == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown sort direction: " + token);
        }
        return direction;
    }

    /** The landing stage is where a company arrives from Strategy, and where a capture lands by default. */
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

    /**
     * Defaults to a hand-typed company, and refuses {@code strategy} outright. A row claiming to come
     * from the market must come through {@link #add}, where the snapshot is resolved from the market
     * and the account id it is keyed by actually exists — V34's CHECK refuses the alternative anyway,
     * and a constraint violation is a worse way to learn it.
     */
    private static TriageCompanySource resolveCapturableSource(String token) {
        if (token == null || token.isBlank()) {
            return TriageCompanySource.MANUAL;
        }
        TriageCompanySource source = TriageCompanySource.fromValue(token);
        if (source == null || !CAPTURABLE_SOURCES.contains(source)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown capture source: " + token);
        }
        return source;
    }

    private void requireProject(UUID projectId, UUID workspaceId) {
        projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }


    private static TriageCompanyResponse toDto(TriageCompany company) {
        return new TriageCompanyResponse(company.getId(), company.getApolloAccountId(),
                company.getSource().value(), company.getStatus().value(), company.getNote(),
                company.getCompanyName(), company.getIndustry(), company.getCompanyCountry(),
                company.getCompanyCity(), company.getNumEmployees(), company.getAnnualRevenue(),
                company.getWebsite(), company.getCompanyLinkedinUrl(), company.getFoundedYear(),
                company.getShortDescription(), company.getSourceUrl(), company.getLogoUrl(),
                company.getCreatedAt());
    }
}
