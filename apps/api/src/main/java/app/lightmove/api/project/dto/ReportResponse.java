package app.lightmove.api.project.dto;

import java.util.List;

/**
 * A mandate's report: what the search's saved scope amounts to, measured against the company
 * universe, plus the compensation band the brief states.
 *
 * <p>Carries only what the client cannot already read from {@code ProjectResponse}, so the screen has
 * one source per fact. {@code mandateBand} is null until the brief carries a salary range — the
 * screen says so rather than showing a band of zero, which would read as a stated figure.
 */
public record ReportResponse(
        long universeCount,
        int offLimitsCompanies,
        int sectorsInScope,
        int marketsInScope,
        List<BreakdownDto> sectors,
        List<BreakdownDto> countries,
        List<BreakdownDto> cities,
        CompensationBandDto mandateBand,
        ScopeCaveatsDto caveats
) {}
