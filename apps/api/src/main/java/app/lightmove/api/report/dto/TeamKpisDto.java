package app.lightmove.api.report.dto;

import java.time.Instant;

/** The two paces are executives per week, over the range and over the whole mandate. */
public record TeamKpisDto(
        int executivesInRange,
        double rangePerWeek,
        double mandatePerWeek,
        int coveredCompanies,
        long targetCompanies,
        Instant lastAddedAt,
        String lastAddedBy
) {}
