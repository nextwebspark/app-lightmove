package app.lightmove.api.strategy.service;

import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.common.location.service.Countries;
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
import app.lightmove.api.strategy.dto.NumericRangeDto;
import app.lightmove.api.strategy.dto.PutOffLimitsRequest;
import app.lightmove.api.strategy.dto.PutStrategyFilterRequest;
import app.lightmove.api.strategy.dto.StrategyCompaniesResponse;
import app.lightmove.api.strategy.dto.StrategyFilterDto;
import app.lightmove.api.strategy.dto.StrategyResponse;
import app.lightmove.api.strategy.model.CompanyExclusion;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.model.NumericRange;
import app.lightmove.api.strategy.model.Strategy;
import app.lightmove.api.strategy.model.StrategyCompanyRef;
import app.lightmove.api.strategy.model.StrategyFilter;
import app.lightmove.api.strategy.repository.StrategyRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A mandate's saved filter, off-limits list, and the page of the universe they select. The company
 * list is resolved server-side from the stored filter, never from client-supplied criteria; the
 * caller supplies only the name filter, page and an allowlisted sort.
 */
@Service
public class StrategyService {

    private static final CompanySortField DEFAULT_SORT = CompanySortField.EMPLOYEES;
    private static final SortDirection DEFAULT_DIRECTION = SortDirection.DESC;

    private final StrategyRepository strategies;
    private final ProjectRepository projects;
    private final StrategySearchService searches;
    private final AuditService audit;
    private final ApolloCompanyQueryService companies;
    private final TriagedCompanyLookup triagedLookup;
    private final CompanyListSettings listConfig;
    private final CompanySearchSettings searchConfig;

    public StrategyService(StrategyRepository strategies, ProjectRepository projects,
                           StrategySearchService searches, AuditService audit,
                           ApolloCompanyQueryService companies, TriagedCompanyLookup triagedLookup,
                           LightMoveProperties properties) {
        this.strategies = strategies;
        this.projects = projects;
        this.searches = searches;
        this.audit = audit;
        this.companies = companies;
        this.triagedLookup = triagedLookup;
        this.listConfig = properties.company().list();
        this.searchConfig = properties.company().search();
    }

    /** Does not seed: the endpoint is WORK_VIEW, and a client's page load must not INSERT. */
    @Transactional(readOnly = true)
    public StrategyResponse get(UUID userId, UUID workspaceId, UUID projectId) {
        projects.requireInWorkspace(projectId, workspaceId);
        return toResponse(strategies.findByProjectId(projectId)
                .orElseGet(() -> Strategy.forProject(projectId)), userId, workspaceId, projectId);
    }

    @Transactional
    public StrategyResponse putFilter(UUID userId, UUID workspaceId, UUID projectId,
                                      PutStrategyFilterRequest request, HttpServletRequest httpRequest) {
        StrategyFilter filter = toFilter(request.filter());

        Strategy strategy = load(projectId, workspaceId);
        strategy.replaceFilter(filter);

        audit.projectEvent(ProjectEventType.STRATEGY_UPDATED, userId, workspaceId, projectId, httpRequest)
                .detail("section", "filter")
                .record();
        return toResponse(strategy, userId, workspaceId, projectId);
    }

    @Transactional
    public StrategyResponse putOffLimits(UUID userId, UUID workspaceId, UUID projectId,
                                         PutOffLimitsRequest request, HttpServletRequest httpRequest) {
        Strategy strategy = load(projectId, workspaceId);
        strategy.replaceOffLimitsCompanies(
                resolveOffLimits(request.apolloAccountIds(), strategy.getOffLimitsCompanies()));

        audit.projectEvent(ProjectEventType.STRATEGY_UPDATED, userId, workspaceId, projectId, httpRequest)
                .detail("section", "offLimits")
                .record();
        return toResponse(strategy, userId, workspaceId, projectId);
    }

    /** Minus whatever this project has already triaged; {@link #scopeOf} keeps the wider scope for bulk writes. */
    @Transactional(readOnly = true)
    public StrategyCompaniesResponse companies(UUID workspaceId, UUID projectId, String query,
                                               String sortToken, String directionToken,
                                               Integer requestedPage, Integer requestedSize) {
        int page = requestedPage == null ? 0 : requestedPage;
        int size = requestedSize == null ? listConfig.defaultPageSize() : requestedSize;
        listConfig.requireValidPage(page, size);
        CompanySortField sort = resolveSort(sortToken);
        SortDirection direction = resolveDirection(directionToken);

        projects.requireInWorkspace(projectId, workspaceId);
        Strategy strategy = strategies.findByProjectId(projectId)
                .orElseGet(() -> Strategy.forProject(projectId));
        CompanyScope scope = StrategyScope.of(strategy, normaliseQuery(query),
                triagedLookup.exclusionFor(projectId));

        List<CompanyRow> rows = companies.search(scope, sort, direction, page, size);
        return new StrategyCompaniesResponse(
                rows.stream().map(CompanyResultDto::of).toList(),
                companies.count(scope), page, size);
    }

    @Transactional(readOnly = true)
    public CompanyScope scopeOf(UUID workspaceId, UUID projectId) {
        projects.requireInWorkspace(projectId, workspaceId);
        return savedScopeOf(projectId, CompanyExclusion.NONE);
    }

    /** What {@link #companies} reads: the scope minus everything already triaged. */
    @Transactional(readOnly = true)
    public CompanyScope untriagedScopeOf(UUID workspaceId, UUID projectId) {
        projects.requireInWorkspace(projectId, workspaceId);
        return savedScopeOf(projectId, triagedLookup.exclusionFor(projectId));
    }

    private CompanyScope savedScopeOf(UUID projectId, CompanyExclusion triagedExclusion) {
        return StrategyScope.of(strategies.findByProjectId(projectId)
                .orElseGet(() -> Strategy.forProject(projectId)), null, triagedExclusion);
    }

    /**
     * A stored id keeps its snapshot — re-resolving would fail the whole save the day one vanishes
     * upstream. Only new ids are resolved, and an unknown one is rejected.
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
     * Only band slugs are validated — a closed catalog we own. A free string the universe no longer
     * carries narrows to nothing rather than 400ing a save the user cannot fix.
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
        // Canonicalised: the filter matches company_country exactly, so a saved "UAE" matched nothing.
        List<String> countries = distinct(dto.countries()).stream()
                .map(Countries::nameOf)
                // A blank country canonicalises to null, and StrategyFilter's List.copyOf throws on one.
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return new StrategyFilter(distinct(dto.industries()), distinct(dto.keywords()),
                distinct(dto.marketSegments()), countries,
                distinct(dto.employeeBands()), distinct(dto.revenueBands()),
                toRange(dto.employeeRange()), toRange(dto.revenueRange()));
    }

    private static List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

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
        return ApiValueEnum.parse(CompanySortField.class, token, DEFAULT_SORT, "sort field");
    }

    private static SortDirection resolveDirection(String token) {
        return ApiValueEnum.parse(SortDirection.class, token, DEFAULT_DIRECTION, "sort direction");
    }

    private Strategy load(UUID projectId, UUID workspaceId) {
        projects.requireInWorkspace(projectId, workspaceId);
        return strategies.findByProjectId(projectId)
                .orElseGet(() -> strategies.save(Strategy.forProject(projectId)));
    }

    private static NumericRange toRange(NumericRangeDto dto) {
        return dto == null ? null : new NumericRange(dto.min(), dto.max());
    }

    /** The caller matters: the searches list hides other people's private ones. */
    private StrategyResponse toResponse(Strategy strategy, UUID userId, UUID workspaceId,
                                        UUID projectId) {
        return new StrategyResponse(
                StrategyFilterDto.of(strategy.getFilter()),
                strategy.getOffLimitsCompanies().stream().map(StrategyService::toDto).toList(),
                searches.list(userId, workspaceId, projectId));
    }

    private static CompanyRefDto toDto(StrategyCompanyRef ref) {
        return new CompanyRefDto(ref.getApolloAccountId(), ref.getCompanyName(), ref.getIndustry(),
                ref.getCompanyCity(), ref.getCompanyCountry(), ref.getLogoUrl());
    }
}
