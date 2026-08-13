package app.lightmove.api.project.service;

import app.lightmove.api.company.service.CompanyQueryService;
import app.lightmove.api.company.service.CompanyQueryService.ScopeBreakdown;
import app.lightmove.api.company.service.CompanyQueryService.ScopeFilter;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.dto.ReportDtos.BreakdownDto;
import app.lightmove.api.project.dto.ReportDtos.CompensationBandDto;
import app.lightmove.api.project.dto.ReportDtos.ReportResponse;
import app.lightmove.api.project.model.Position;
import app.lightmove.api.project.model.Strategy;
import app.lightmove.api.project.repository.PositionRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.project.repository.StrategyRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read behind a mandate's Reports screen: the shape of the universe the saved Strategy scopes,
 * aggregated live rather than stored. A report nobody generated is still a true report — the figures
 * are a view of the scope as it stands, so a snapshot table would only let the screen go stale.
 *
 * <p>The scope resolves through {@link StrategyScope}, the same translation Sourcing uses, so the
 * report's totals and the Sourcing list can never disagree about which companies are in the search.
 *
 * <p>Deliberately narrow: everything the mockup's report derives from mapped executives has no data
 * behind it yet, and the screen says so rather than being handed a zero to render as a finding.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** Enough bars to show the shape without turning a wide scope into a wall of one-company rows. */
    private static final int SECTOR_LIMIT = 10;
    private static final int COUNTRY_LIMIT = 8;
    private static final int CITY_LIMIT = 8;

    private final ProjectRepository projects;
    private final StrategyRepository strategies;
    private final PositionRepository positions;
    private final CompanyQueryService companies;

    @Transactional(readOnly = true)
    public ReportResponse get(UUID workspaceId, UUID projectId) {
        requireProject(projectId, workspaceId);
        // Unlike the Strategy screen's own read, an unsaved strategy is not seeded here: a report is a
        // read, and writing a row to answer one would make a client representative's page load a write.
        Strategy strategy = strategies.findByProjectId(projectId)
                .orElseGet(() -> Strategy.forProject(projectId));
        ScopeFilter scope = StrategyScope.of(strategy, null);

        return new ReportResponse(
                companies.estimate(scope),
                strategy.getTargetCompanies().size(),
                strategy.getOffLimitsCompanies().size(),
                scope.directSectors().size() + scope.adjacentSectors().size(),
                scope.markets().size(),
                toDtos(companies.countByMatchTier(scope)),
                toDtos(companies.countBySector(scope, SECTOR_LIMIT)),
                toDtos(companies.countByCountry(scope, COUNTRY_LIMIT)),
                toDtos(companies.countByCity(scope, CITY_LIMIT)),
                mandateBandOf(positions.findByProjectId(projectId)));
    }

    private void requireProject(UUID projectId, UUID workspaceId) {
        projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /** A band with neither bound is no band at all — null, so the screen states its absence. */
    private static CompensationBandDto mandateBandOf(Optional<Position> position) {
        return position
                .filter(brief -> brief.getSalaryMin() != null || brief.getSalaryMax() != null)
                .map(brief -> new CompensationBandDto(
                        brief.getSalaryMin(), brief.getSalaryMax(), brief.getCurrency()))
                .orElse(null);
    }

    private static List<BreakdownDto> toDtos(List<ScopeBreakdown> rows) {
        return rows.stream().map(row -> new BreakdownDto(row.label(), row.count())).toList();
    }
}
