package app.lightmove.api.project.dto;

import java.util.List;

/**
 * The HTTP contract for a mandate's report: what the search's saved scope amounts to, measured against
 * the company universe, plus the compensation band the brief states.
 *
 * <p>Carries only what the client cannot already read from {@code ProjectDtos.ProjectResponse} — the
 * position title, client, stage, target date and team seats all arrive with the project itself, and
 * duplicating them here would give the screen two sources for the same fact.
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    /**
     * {@code mandateBand} is null until the position brief carries a salary range — the screen says so
     * rather than showing a band of zero, which would read as a stated figure.
     */
    public record ReportResponse(
            long universeCount,
            int targetCompanies,
            int offLimitsCompanies,
            int sectorsInScope,
            int marketsInScope,
            List<BreakdownDto> relevance,
            List<BreakdownDto> sectors,
            List<BreakdownDto> countries,
            List<BreakdownDto> cities,
            CompensationBandDto mandateBand
    ) {}

    /** One labelled slice of the scoped universe — a sector, a country, a city, a match tier. */
    public record BreakdownDto(String label, long count) {}

    /** The band the brief asks for. Either bound may be null; the pair is absent when both are. */
    public record CompensationBandDto(Long min, Long max, String currency) {}
}
