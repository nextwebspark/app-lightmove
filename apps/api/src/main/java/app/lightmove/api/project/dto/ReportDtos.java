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
            CompensationBandDto mandateBand,
            ScopeCaveatsDto caveats
    ) {}

    /**
     * Where the report's source could not answer the mandate's scope in full. Every figure above is a
     * stated measurement, so the ways the measurement is narrower than the scope travel with it rather
     * than being left for the reader to infer from a number that looks lower than it should.
     */
    public record ScopeCaveatsDto(
            /* Barred companies that could not be excluded: the off-limits list is keyed on the
             * warehouse's (source, source_id), which this source has no counterpart for. */
            int offLimitsNotApplied,
            /* Selected sectors this source does not carry at all — the difference between "the market
             * is empty" and "we cannot see this part of it". */
            List<String> sectorsNotInSource,
            /* True when a revenue band is selected: this source carries a revenue figure on a minority
             * of rows, and one without a figure cannot be shown to fall in the band. */
            boolean revenueBandExcludesUnknown
    ) {}

    /** One labelled slice of the scoped universe — a sector, a country, a city, a match tier. */
    public record BreakdownDto(String label, long count) {}

    /** The band the brief asks for. Either bound may be null; the pair is absent when both are. */
    public record CompensationBandDto(Long min, Long max, String currency) {}
}
