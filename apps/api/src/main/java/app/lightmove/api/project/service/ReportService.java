package app.lightmove.api.project.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.dto.BreakdownDto;
import app.lightmove.api.project.dto.CompensationBandDto;
import app.lightmove.api.project.dto.ReportResponse;
import app.lightmove.api.project.dto.ScopeCaveatsDto;
import app.lightmove.api.project.model.Position;
import app.lightmove.api.project.repository.PositionRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.strategy.constant.RevenueBand;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.model.ScopeBreakdown;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.strategy.service.StrategyService;
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
 * <p>The scope resolves through {@link StrategyScope} — the same translation the Strategy screen's own
 * list uses, against the same table. That is new: the report used to measure Apollo while triage
 * measured the brightdata warehouse copy, so the two legitimately disagreed on every count and the
 * difference had to be explained in caveats. With one universe they agree, and two of the three
 * caveats have gone with the second source.
 *
 * <p>One remains, and it is about the data rather than the plumbing: Apollo publishes a revenue figure
 * on 7,132 of 71,822 rows, so a revenue-scoped report is measuring a tenth of the market unless the
 * Unknown band is among those selected. That is reported beside the figures rather than silently
 * lowering them.
 *
 * <p>Deliberately narrow: everything the mockup's report derives from mapped executives has no data
 * behind it yet, and the screen says so rather than being handed a zero to render as a finding.
 *
 * <p>This is a sanctioned {@code project} → {@code strategy} seam, and it is deliberately one method
 * wide: {@link StrategyService#scopeOf} hands back the resolved scope, so the report never learns how
 * a filter is stored, validated or translated. The universe read beside it crosses into the same
 * feature, and is the same shape of seam: one public method over {@code strategy}'s own records.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** Enough bars to show the shape without turning a wide scope into a wall of one-company rows. */
    private static final int SECTOR_LIMIT = 10;
    private static final int COUNTRY_LIMIT = 8;
    private static final int CITY_LIMIT = 8;

    private final ProjectRepository projects;
    // The one thing this feature needs from strategy: the scope a mandate's saved filter defines.
    // A single public method, so the report never learns how a filter is stored or resolved.
    private final StrategyService strategy;
    private final PositionRepository positions;
    private final ApolloCompanyQueryService companies;

    @Transactional(readOnly = true)
    public ReportResponse get(UUID workspaceId, UUID projectId) {
        requireProject(projectId, workspaceId);
        // Unlike the Strategy screen's own read, an unsaved strategy is not seeded here: a report is a
        // read, and writing a row to answer one would make a client representative's page load a write.
        // scopeOf resolves the mandate's project against the workspace a second time; that is a cheap
        // lookup and the alternative is a method that trusts a project id it was handed.
        CompanyScope scope = strategy.scopeOf(workspaceId, projectId);

        return new ReportResponse(
                companies.count(scope),
                scope.offLimitsAccountIds().size(),
                scope.industries().size(),
                scope.countries().size(),
                toDtos(companies.countBySector(scope, SECTOR_LIMIT)),
                toDtos(companies.countByCountry(scope, COUNTRY_LIMIT)),
                toDtos(companies.countByCity(scope, CITY_LIMIT)),
                mandateBandOf(positions.findByProjectId(projectId)),
                new ScopeCaveatsDto(excludesUnknownRevenue(scope)));
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

    /**
     * True when the scope narrows by revenue without taking the Unknown band with it — the one case
     * where the figures below describe a tenth of the market and look like the whole of it.
     */
    private static boolean excludesUnknownRevenue(CompanyScope scope) {
        return !scope.revenueBands().isEmpty()
                && !scope.revenueBands().contains(RevenueBand.R_UNKNOWN.value());
    }

    private static List<BreakdownDto> toDtos(List<ScopeBreakdown> rows) {
        return rows.stream().map(row -> new BreakdownDto(row.label(), row.count())).toList();
    }
}
