package app.lightmove.api.strategy.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.CompanyListSettings;
import app.lightmove.api.core.config.CompanySearchSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.strategy.constant.CompanySortField;
import app.lightmove.api.strategy.constant.EmployeeBand;
import app.lightmove.api.strategy.constant.RevenueBand;
import app.lightmove.api.strategy.constant.SortDirection;
import app.lightmove.api.strategy.dto.CompanyRefDto;
import app.lightmove.api.strategy.dto.CompanyResultDto;
import app.lightmove.api.strategy.dto.PutOffLimitsRequest;
import app.lightmove.api.strategy.dto.PutStrategyFilterRequest;
import app.lightmove.api.strategy.dto.StrategyCompaniesResponse;
import app.lightmove.api.strategy.dto.NumericRangeDto;
import app.lightmove.api.strategy.dto.StrategyFilterDto;
import app.lightmove.api.strategy.dto.StrategyResponse;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.model.Strategy;
import app.lightmove.api.strategy.model.StrategyCompanyRef;
import app.lightmove.api.strategy.model.NumericRange;
import app.lightmove.api.strategy.model.StrategyFilter;
import app.lightmove.api.strategy.repository.StrategyRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The search behind a project: its saved filter, its off-limits list, and the filtered page of the
 * universe both add up to.
 *
 * <p>Every load is scoped through the project's {@code (id, workspaceId)} lookup — the workspace id
 * comes from the principal, so a foreign project 404s before any strategy row is touched. The
 * strategy is seeded empty on first read: there is no template, and an empty filter is the honest
 * start, matching the whole universe rather than nothing.
 *
 * <p>The company list is resolved <b>entirely server-side from the stored filter</b>, never from
 * client-supplied industry or band lists. A mandate's chosen scope is team-only content, which is why
 * this sits behind the project-level gates while the universe's own facet counts — the same for every
 * mandate — are a workspace-level read. The name filter, page and sort are the one thing the caller
 * does supply, and none of them widens what they can see: the filter only narrows the scope already
 * fixed, and the sort resolves through {@link CompanySortField} so a caller names a column from a
 * closed catalog rather than handing us SQL.
 *
 * <p>The filter and the universe read it drives are both strategy's own, which is why they sit in one
 * feature: a band or a sector group is a way of asking the market a question, not a property of the
 * mandate asking it. What this service does <b>not</b> own is the answer a mandate then records about
 * a company — that is a project-to-company row with a triage status, and it lives in
 * {@code triagecompany}, which depends on {@link #scopeOf} rather than the other way round.
 */
@Service
public class StrategyService {

    /** What the table sorts by until the user picks a column. Largest first is a defensible default. */
    private static final CompanySortField DEFAULT_SORT = CompanySortField.EMPLOYEES;
    private static final SortDirection DEFAULT_DIRECTION = SortDirection.DESC;

    private final StrategyRepository strategies;
    private final ProjectRepository projects;
    private final StrategySearchService searches;
    private final AuditService audit;
    private final ApolloCompanyQueryService companies;
    private final CompanyListSettings listConfig;
    private final CompanySearchSettings searchConfig;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public StrategyService(StrategyRepository strategies, ProjectRepository projects,
                           StrategySearchService searches, AuditService audit,
                           ApolloCompanyQueryService companies, LightMoveProperties properties) {
        this.strategies = strategies;
        this.projects = projects;
        this.searches = searches;
        this.audit = audit;
        this.companies = companies;
        this.listConfig = properties.company().list();
        this.searchConfig = properties.company().search();
    }

    /**
     * The screen's first read. It does not seed: the endpoint is WORK_VIEW, so a client representative
     * opening the tab would otherwise perform an INSERT to answer their own page load. An unsaved
     * mandate answers from a transient row and only the write paths persist one.
     */
    @Transactional(readOnly = true)
    public StrategyResponse get(UUID workspaceId, UUID projectId) {
        requireProject(projectId, workspaceId);
        return toResponse(strategies.findByProjectId(projectId)
                .orElseGet(() -> Strategy.forProject(projectId)), workspaceId, projectId);
    }

    @Transactional
    public StrategyResponse putFilter(UUID userId, UUID workspaceId, UUID projectId,
                                      PutStrategyFilterRequest request, HttpServletRequest httpRequest) {
        StrategyFilter filter = toFilter(request.filter());

        Strategy strategy = load(projectId, workspaceId);
        strategy.replaceFilter(filter);

        audit.event(ProjectEventType.STRATEGY_UPDATED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("section", "filter")
                .record();
        return toResponse(strategy, workspaceId, projectId);
    }

    @Transactional
    public StrategyResponse putOffLimits(UUID userId, UUID workspaceId, UUID projectId,
                                         PutOffLimitsRequest request, HttpServletRequest httpRequest) {
        Strategy strategy = load(projectId, workspaceId);
        strategy.replaceOffLimitsCompanies(
                resolveOffLimits(request.apolloAccountIds(), strategy.getOffLimitsCompanies()));

        audit.event(ProjectEventType.STRATEGY_UPDATED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("section", "offLimits")
                .record();
        return toResponse(strategy, workspaceId, projectId);
    }

    /** One page of the universe as the mandate's filter narrows it. */
    @Transactional(readOnly = true)
    public StrategyCompaniesResponse companies(UUID workspaceId, UUID projectId, String query,
                                               String sortToken, String directionToken,
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
        CompanySortField sort = resolveSort(sortToken);
        SortDirection direction = resolveDirection(directionToken);

        requireProject(projectId, workspaceId);
        Strategy strategy = strategies.findByProjectId(projectId)
                .orElseGet(() -> Strategy.forProject(projectId));
        CompanyScope scope = StrategyScope.of(strategy, normaliseQuery(query));

        List<CompanyRow> rows = companies.search(scope, sort, direction, page, size);
        return new StrategyCompaniesResponse(
                rows.stream().map(StrategyService::toDto).toList(),
                companies.count(scope), page, size);
    }

    /** The scope a mandate's filter currently defines, for the callers that act on it in bulk. */
    @Transactional(readOnly = true)
    public CompanyScope scopeOf(UUID workspaceId, UUID projectId) {
        requireProject(projectId, workspaceId);
        return StrategyScope.of(strategies.findByProjectId(projectId)
                .orElseGet(() -> Strategy.forProject(projectId)));
    }

    /**
     * Turn the requested ids into the refs to store. An id already on the list keeps its stored
     * snapshot untouched — re-resolving it would make removing one company fail the whole save the day
     * another vanishes upstream. Only new ids are resolved against the universe, and an unknown one is
     * rejected: the client may only bar companies that exist.
     */
    private List<StrategyCompanyRef> resolveOffLimits(List<String> requested,
                                                      List<StrategyCompanyRef> stored) {
        Map<String, StrategyCompanyRef> storedById = new HashMap<>();
        for (StrategyCompanyRef ref : stored) {
            storedById.put(ref.getApolloAccountId(), ref);
        }

        Set<String> seen = new LinkedHashSet<>();
        List<String> newIds = new ArrayList<>();
        for (String id : requested) {
            if (!seen.add(id)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Duplicate company on the off-limits list: " + id);
            }
            if (!storedById.containsKey(id)) {
                newIds.add(id);
            }
        }

        Map<String, CompanyRow> resolved = new HashMap<>();
        for (CompanyRow row : companies.byAccountIds(newIds)) {
            resolved.put(row.apolloAccountId(), row);
        }

        List<StrategyCompanyRef> refs = new ArrayList<>(requested.size());
        for (String id : requested) {
            StrategyCompanyRef kept = storedById.get(id);
            if (kept != null) {
                refs.add(kept);
                continue;
            }
            CompanyRow row = resolved.get(id);
            if (row == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Not in the universe: " + id);
            }
            refs.add(StrategyCompanyRef.of(row));
        }
        return refs;
    }

    /**
     * Validate the submitted filter against the catalogs the universe actually offers. Industries,
     * market segments and countries are free strings — they come from the facets response verbatim,
     * and a value the universe has stopped carrying should narrow to nothing rather than 400 a save
     * the user cannot fix. Band slugs are different: they name a closed catalog this codebase owns, so
     * an unknown one is a client bug and says so.
     */
    private static StrategyFilter toFilter(StrategyFilterDto dto) {
        for (String band : dto.employeeBands()) {
            if (EmployeeBand.fromValue(band) == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown employee band: " + band);
            }
        }
        for (String band : dto.revenueBands()) {
            if (RevenueBand.fromValue(band) == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown revenue band: " + band);
            }
        }
        return new StrategyFilter(distinct(dto.industries()), distinct(dto.marketSegments()),
                distinct(dto.countries()), distinct(dto.employeeBands()), distinct(dto.revenueBands()),
                toRange(dto.employeeRange()), toRange(dto.revenueRange()));
    }

    /**
     * Selections are sets the client renders as chips; a repeat is a client bug that would only widen
     * the stored document without changing the query. De-duplicated in request order rather than
     * rejected — unlike a duplicate on the off-limits list, this one has an obvious right answer.
     */
    private static List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    /** Blank is no filter; anything longer than a company name is a mistake, not a search. */
    private String normaliseQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String trimmed = query.trim();
        if (trimmed.length() > searchConfig.maxQueryLength()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "q exceeds " + searchConfig.maxQueryLength() + " characters");
        }
        return trimmed;
    }

    private static CompanySortField resolveSort(String token) {
        if (token == null || token.isBlank()) {
            return DEFAULT_SORT;
        }
        CompanySortField sort = CompanySortField.fromValue(token);
        if (sort == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown sort field: " + token);
        }
        return sort;
    }

    private static SortDirection resolveDirection(String token) {
        if (token == null || token.isBlank()) {
            return DEFAULT_DIRECTION;
        }
        SortDirection direction = SortDirection.fromValue(token);
        if (direction == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown sort direction: " + token);
        }
        return direction;
    }

    private Strategy load(UUID projectId, UUID workspaceId) {
        requireProject(projectId, workspaceId);
        return strategies.findByProjectId(projectId)
                .orElseGet(() -> strategies.save(Strategy.forProject(projectId)));
    }

    private void requireProject(UUID projectId, UUID workspaceId) {
        projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /** Bounds are already validated by the DTO; this is the shape change only. */
    private static NumericRange toRange(NumericRangeDto dto) {
        return dto == null ? null : new NumericRange(dto.min(), dto.max());
    }

    private StrategyResponse toResponse(Strategy strategy, UUID workspaceId, UUID projectId) {
        return new StrategyResponse(
                StrategyFilterDto.of(strategy.getFilter()),
                strategy.getOffLimitsCompanies().stream().map(StrategyService::toDto).toList(),
                searches.list(workspaceId, projectId));
    }

    private static CompanyRefDto toDto(StrategyCompanyRef ref) {
        return new CompanyRefDto(ref.getApolloAccountId(), ref.getCompanyName(), ref.getIndustry(),
                ref.getCompanyCity(), ref.getCompanyCountry(), ref.getLogoUrl());
    }

    /** One argument per line: twenty-seven positional arguments, and a misplaced one still compiles. */
    private static CompanyResultDto toDto(CompanyRow row) {
        return new CompanyResultDto(
                row.apolloAccountId(),
                row.companyName(),
                row.industry(),
                row.companyCountry(),
                row.companyCity(),
                row.numEmployees(),
                row.annualRevenue(),
                row.website(),
                row.logoUrl(),
                row.shortDescription(),
                row.foundedYear(),
                row.companyLinkedinUrl(),
                row.facebookUrl(),
                row.twitterUrl(),
                row.companyPhone(),
                row.companyState(),
                row.companyAddress(),
                row.parentCompany(),
                row.totalFunding(),
                row.latestFunding(),
                row.latestFundingAmount(),
                row.lastRaisedAt(),
                row.numberOfRetailLocations(),
                row.keywords(),
                row.technologies(),
                row.sicCodes(),
                row.naicsCodes());
    }
}
