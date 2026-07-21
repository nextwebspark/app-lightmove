package app.lightmove.api.project.service;

import app.lightmove.api.company.constant.CompanySizeAxis;
import app.lightmove.api.company.constant.EmployeeBand;
import app.lightmove.api.company.constant.RevenueBand;
import app.lightmove.api.company.model.CompanyKey;
import app.lightmove.api.company.service.CompanyQueryService;
import app.lightmove.api.company.service.CompanyQueryService.CompanyRow;
import app.lightmove.api.company.service.CompanyQueryService.ScopeFilter;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.constant.GeographyMarket;
import app.lightmove.api.project.constant.StrategySectorKind;
import app.lightmove.api.project.dto.SourcingDtos.AppliedFilters;
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

    private final StrategyRepository strategies;
    private final ProjectRepository projects;
    private final CompanyQueryService companies;

    @Transactional(readOnly = true)
    public SourcingResponse get(UUID workspaceId, UUID projectId, int page, int size) {
        if (page < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "size must be between 1 and " + MAX_PAGE_SIZE);
        }

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
                revenueBands, markets, targetKeys, offLimitsKeys);
        List<CompanyRow> rows = companies.search(scope, page, size);
        long totalCount = companies.estimate(scope);

        List<String> allSectors = new ArrayList<>(directSectors);
        allSectors.addAll(adjacentSectors);
        AppliedFilters appliedFilters = new AppliedFilters(!allSectors.isEmpty() || !tags.isEmpty(),
                !employeeBands.isEmpty(), !revenueBands.isEmpty(), !markets.isEmpty());
        return new SourcingResponse(
                rows.stream().map(SourcingService::toDto).toList(), totalCount, page, size, appliedFilters);
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
        return new CompanyResultDto(row.id(), row.name(), row.domain(), row.primaryIndustry(),
                row.employeeRange(), row.revenueRange(), locationOf(row), row.matchTier());
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
