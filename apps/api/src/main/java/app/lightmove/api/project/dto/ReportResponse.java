package app.lightmove.api.project.dto;

import java.util.List;

/**
 * A mandate's report: what the search's saved scope amounts to, measured against the company
 * universe, plus the compensation band the brief states.
 *
 * <p>Carries only what the client cannot already read from {@code ProjectResponse} — the position
 * title, client, stage, target date and team seats all arrive with the project itself, and
 * duplicating them here would give the screen two sources for the same fact.
 *
 * <p>{@code mandateBand} is null until the position brief carries a salary range — the screen says so
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
