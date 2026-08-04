package app.lightmove.api.project.service;

import app.lightmove.api.company.constant.CompanySizeAxis;
import app.lightmove.api.company.constant.CompanySortField;
import app.lightmove.api.company.constant.EmployeeBand;
import app.lightmove.api.company.constant.RevenueBand;
import app.lightmove.api.company.constant.SortDirection;
import app.lightmove.api.company.model.CompanyKey;
import app.lightmove.api.company.service.CompanyQueryService;
import app.lightmove.api.company.service.CompanyQueryService.CompanyRow;
import app.lightmove.api.company.service.CompanyQueryService.ScopeFilter;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.constant.GeographyMarket;
import app.lightmove.api.project.constant.StrategySectorKind;
import app.lightmove.api.project.dto.SourcingDtos.CompanyResultDto;
import app.lightmove.api.project.dto.SourcingDtos.SourcingResponse;
import app.lightmove.api.project.model.Strategy;
import app.lightmove.api.project.model.StrategyCompanyRef;
import app.lightmove.api.project.model.StrategySizeBand;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.project.repository.StrategyRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The companies matching a project's saved Strategy scope — the read behind the Sourcing screen. The
 * scope is resolved entirely server-side from the stored {@link Strategy} (never from client-supplied
 * sector/tag/band lists): a mandate's chosen scope is team-only content, the same reason
 * {@code StrategyController} sits behind {@code WORK_EXECUTE} rather than the workspace-level
 * {@code PROJECT_BROWSE} that gates the shared, caller-parameterised company reads.
 *
 * <p>The name filter and column sort are the one thing the caller does supply, and neither widens what
 * they can see: the filter only ever narrows the scope the Strategy already fixed, and the sort resolves
 * through {@link CompanySortField} so a caller names a column from a closed catalog rather than handing
 * us SQL.
 *
 * <p>This is a deliberate, narrow exception to "features depend on core, never on each other's
 * internals": Sourcing's job is running the {@code company} feature's shared
 * {@link CompanyQueryService} against sizing/sector criteria this feature already owns — the same shape
 * as the documented exceptions for {@code AuthResponseAssembler} and the {@code rbac} access services.
 */
@Service
@RequiredArgsConstructor
public class SourcingService {

    /** A generous ceiling on page size — a scope, not an attack. */
    public static final int MAX_PAGE_SIZE = 100;

    /** The same for a name filter: longer than any company name, short enough not to be a payload. */
    public static final int MAX_QUERY_LENGTH = 100;

    private final StrategyRepository strategies;
    private final ProjectRepository projects;
    private final CompanyQueryService companies;

    @Transactional(readOnly = true)
    public SourcingResponse get(UUID workspaceId, UUID projectId, String query, String sortToken,
                                String directionToken, int page, int size) {
        if (page < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        String nameQuery = normaliseQuery(query);
        CompanySortField sort = resolveSort(sortToken);
        SortDirection direction = resolveDirection(directionToken);

        requireProject(projectId, workspaceId);
        Strategy strategy = strategies.findByProjectId(projectId).orElseGet(() -> Strategy.forProject(projectId));

        List<String> directSectors = labelsOf(strategy, StrategySectorKind.DIRECT);
        List<String> adjacentSectors = labelsOf(strategy, StrategySectorKind.ADJACENT);
        List<String> tags = labelsOf(strategy, StrategySectorKind.INFERRED);
        List<String> employeeBands = employeeBandsOf(strategy);
        List<String> revenueBands = revenueBandsOf(strategy);
        List<String> markets = marketsOf(strategy);
        List<CompanyKey> targetKeys = keysOf(strategy.getTargetCompanies());
        List<CompanyKey> offLimitsKeys = keysOf(strategy.getOffLimitsCompanies());

        ScopeFilter scope = new ScopeFilter(directSectors, adjacentSectors, tags, employeeBands,
                revenueBands, markets, targetKeys, offLimitsKeys, nameQuery);
        List<CompanyRow> rows = companies.search(scope, sort, direction, page, size);
        long totalCount = companies.estimate(scope);
        return new SourcingResponse(
                rows.stream().map(SourcingService::toDto).toList(), totalCount, page, size);
    }

    /** Blank is no filter; anything longer than a company name is a mistake, not a search. */
    private static String normaliseQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String trimmed = query.trim();
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "query must be at most " + MAX_QUERY_LENGTH + " characters");
        }
        return trimmed;
    }

    /**
     * An unknown sort token is rejected rather than ignored: a table that silently keeps its old order
     * after a header click reads as a broken table, and the client's tokens come from a mirror of the
     * same enum, so an unrecognised one is a drift bug worth surfacing.
     */
    private static CompanySortField resolveSort(String sortToken) {
        if (sortToken == null || sortToken.isBlank()) {
            return null;
        }
        CompanySortField sort = CompanySortField.fromValue(sortToken.trim());
        if (sort == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown sort field: " + sortToken);
        }
        return sort;
    }

    /** Ascending by default — a direction without a sort field orders nothing either way. */
    private static SortDirection resolveDirection(String directionToken) {
        if (directionToken == null || directionToken.isBlank()) {
            return SortDirection.ASC;
        }
        SortDirection direction = SortDirection.fromValue(directionToken.trim());
        if (direction == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown sort direction: " + directionToken);
        }
        return direction;
    }

    private void requireProject(UUID projectId, UUID workspaceId) {
        projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /** Selected sector labels of the given kinds — DIRECT+ADJACENT are sectors, INFERRED are tags. */
    private static List<String> labelsOf(Strategy strategy, StrategySectorKind... kinds) {
        List<String> labels = new ArrayList<>();
        for (var sector : strategy.getSectors()) {
            if (sector.isSelected() && isOneOf(sector.getKind(), kinds)) {
                labels.add(sector.getLabel());
            }
        }
        return labels;
    }

    private static boolean isOneOf(StrategySectorKind kind, StrategySectorKind[] kinds) {
        for (StrategySectorKind candidate : kinds) {
            if (candidate == kind) {
                return true;
            }
        }
        return false;
    }

    /** Selected employee bands, as their wire-format range strings. Presence in the list is selection. */
    private static List<String> employeeBandsOf(Strategy strategy) {
        List<String> bands = new ArrayList<>();
        for (StrategySizeBand band : strategy.getSizeBands()) {
            if (band.getAxis() == CompanySizeAxis.EMPLOYEE) {
                bands.add(EmployeeBand.valueOf(band.getBand()).value());
            }
        }
        return bands;
    }

    /** Selected revenue bands, as their wire-format range strings. Presence in the list is selection. */
    private static List<String> revenueBandsOf(Strategy strategy) {
        List<String> bands = new ArrayList<>();
        for (StrategySizeBand band : strategy.getSizeBands()) {
            if (band.getAxis() == CompanySizeAxis.REVENUE) {
                bands.add(RevenueBand.valueOf(band.getBand()).value());
            }
        }
        return bands;
    }

    /** Selected markets, resolved from the stored enum names to their wire ISO codes. */
    private static List<String> marketsOf(Strategy strategy) {
        return strategy.getMarketNames().stream().map(name -> GeographyMarket.valueOf(name).value()).toList();
    }

    private static List<CompanyKey> keysOf(List<StrategyCompanyRef> refs) {
        return refs.stream().map(ref -> new CompanyKey(ref.getSource(), ref.getSourceId())).toList();
    }

    private static CompanyResultDto toDto(CompanyRow row) {
        return new CompanyResultDto(row.id(), row.name(), row.domain(), row.website(), row.linkedinUrl(),
                row.logo(), row.slogan(), row.description(), row.primaryIndustry(), row.industryTags(),
                row.specialties(), row.hqCountry(), locationOf(row), row.employeeRange(),
                row.revenueRange(), row.founded(), row.ownership(), row.ipoStatus(), row.orgType(),
                row.matchTier());
    }

    private static String locationOf(CompanyRow row) {
        String city = row.hqCity();
        String country = row.hqCountry();
        if (city == null || city.isBlank()) {
            return country == null ? "" : country;
        }
        if (country == null || country.isBlank()) {
            return city;
        }
        return city + ", " + country;
    }
}
