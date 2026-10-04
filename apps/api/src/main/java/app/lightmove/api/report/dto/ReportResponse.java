package app.lightmove.api.report.dto;

/** A mandate's talent mapping report, aggregated live at read time; nothing is stored. */
public record ReportResponse(
        ReportHeadDto head,
        MappingProgressDto progress,
        MarketShapeDto market,
        RemunerationDto remuneration,
        DiversityDto diversity
) {}
