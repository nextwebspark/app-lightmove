package app.lightmove.api.project.service;

import app.lightmove.api.company.service.ApolloCompanyQueryService;
import app.lightmove.api.company.model.ScopeBreakdown;
import app.lightmove.api.company.model.ScopeFilter;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.dto.BreakdownDto;
import app.lightmove.api.project.dto.CompensationBandDto;
import app.lightmove.api.project.dto.ReportResponse;
import app.lightmove.api.project.dto.ScopeCaveatsDto;
import app.lightmove.api.project.model.Position;
import app.lightmove.api.project.model.Strategy;
import app.lightmove.api.project.repository.PositionRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.project.repository.StrategyRepository;
import java.util.ArrayList;
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
 * <p>The scope resolves through {@link StrategyScope} — the same translation Sourcing uses — but it is
 * measured against a <i>different source</i>: {@link ApolloCompanyQueryService} reads
 * {@code app_lm_apollo_companies}, where Sourcing reads the brightdata warehouse copy. <b>The two will
 * therefore disagree on counts, by design.</b> Apollo cannot answer the whole scope either: the
 * off-limits list has no key there, its industry vocabulary covers a fraction of the labels the
 * Strategy screen offers, and its revenue figure is sparse — so each shortfall is reported as a caveat
 * beside the figures rather than silently lowering them.
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
    private final ApolloCompanyQueryService companies;

    @Transactional(readOnly = true)
    public ReportResponse get(UUID workspaceId, UUID projectId) {
        requireProject(projectId, workspaceId);
        // Unlike the Strategy screen's own read, an unsaved strategy is not seeded here: a report is a
        // read, and writing a row to answer one would make a client representative's page load a write.
        Strategy strategy = strategies.findByProjectId(projectId)
                .orElseGet(() -> Strategy.forProject(projectId));
        ScopeFilter scope = StrategyScope.of(strategy, null);

        List<String> selectedSectors = new ArrayList<>(scope.directSectors());
        selectedSectors.addAll(scope.adjacentSectors());

        return new ReportResponse(
                companies.estimate(scope),
                strategy.getTargetCompanies().size(),
                strategy.getOffLimitsCompanies().size(),
                selectedSectors.size(),
                scope.markets().size(),
                toDtos(companies.countByMatchTier(scope)),
                toDtos(companies.countBySector(scope, SECTOR_LIMIT)),
                toDtos(companies.countByCountry(scope, COUNTRY_LIMIT)),
                toDtos(companies.countByCity(scope, CITY_LIMIT)),
                mandateBandOf(positions.findByProjectId(projectId)),
                new ScopeCaveatsDto(
                        strategy.getOffLimitsCompanies().size(),
                        companies.sectorsAbsentFromSource(selectedSectors),
                        !scope.revenueBands().isEmpty()));
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
