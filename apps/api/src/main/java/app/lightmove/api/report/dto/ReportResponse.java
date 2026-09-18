package app.lightmove.api.report.dto;

/**
 * A mandate's talent mapping report, read whole: the head figures and the four chapters the screen
 * walks. Everything in it is aggregated live from the mandate's own rows at the moment of the read;
 * nothing is stored, so it can never go stale.
 */
public record ReportResponse(
        ReportHeadDto head,
        MappingProgressDto progress,
        MarketShapeDto market,
        RemunerationDto remuneration,
        DiversityDto diversity
) {}
