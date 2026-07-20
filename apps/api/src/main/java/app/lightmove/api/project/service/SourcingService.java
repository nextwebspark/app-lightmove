package app.lightmove.api.project.service;

import app.lightmove.api.company.service.CompanyQueryService;
import app.lightmove.api.company.service.CompanyQueryService.CompanyRow;
import app.lightmove.api.company.service.CompanyQueryService.Range;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.constant.CompanySizeAxis;
import app.lightmove.api.project.constant.EmployeeBand;
import app.lightmove.api.project.constant.RevenueBand;
import app.lightmove.api.project.constant.StrategySectorKind;
import app.lightmove.api.project.dto.SourcingDtos.CompanyResultDto;
import app.lightmove.api.project.dto.SourcingDtos.SourcingResponse;
import app.lightmove.api.project.model.Strategy;
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

        List<String> sectors = labelsOf(strategy, StrategySectorKind.DIRECT, StrategySectorKind.ADJACENT);
        List<String> tags = labelsOf(strategy, StrategySectorKind.INFERRED);
        List<Range> employeeRanges = rangesOf(strategy, CompanySizeAxis.EMPLOYEE);
        List<Range> revenueRanges = rangesOf(strategy, CompanySizeAxis.REVENUE);

        List<CompanyRow> rows = companies.search(sectors, tags, employeeRanges, revenueRanges, page, size);
        long totalCount = companies.estimate(sectors, tags, employeeRanges, revenueRanges);

        return new SourcingResponse(rows.stream().map(SourcingService::toDto).toList(), totalCount, page, size);
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

    /** Selected bands on one axis, resolved to their numeric bounds. Presence in the list is selection. */
    private static List<Range> rangesOf(Strategy strategy, CompanySizeAxis axis) {
        List<Range> ranges = new ArrayList<>();
        for (StrategySizeBand band : strategy.getSizeBands()) {
            if (band.getAxis() != axis) {
                continue;
            }
            ranges.add(axis == CompanySizeAxis.EMPLOYEE
                    ? employeeRange(EmployeeBand.valueOf(band.getBand()))
                    : revenueRange(RevenueBand.valueOf(band.getBand())));
        }
        return ranges;
    }

    private static Range employeeRange(EmployeeBand band) {
        Long max = band.maxCount() == null ? null : band.maxCount().longValue();
        return new Range((long) band.minCount(), max);
    }

    private static Range revenueRange(RevenueBand band) {
        return new Range(band.minUsd(), band.maxUsd());
    }

    private static CompanyResultDto toDto(CompanyRow row) {
        return new CompanyResultDto(row.id(), row.name(), row.domain(), row.primaryIndustry(),
                row.employeeRange(), row.revenueRange(), locationOf(row));
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
