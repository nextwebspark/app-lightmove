package app.lightmove.api.report.dto;

import java.time.Instant;

/**
 * The card's four headline figures. The two paces are executives per week, over the range and over
 * the whole mandate, so the range reads against the mandate's own habit rather than a guessed target.
 */
public record TeamKpisDto(
        int executivesInRange,
        double rangePerWeek,
        double mandatePerWeek,
        int coveredCompanies,
        long targetCompanies,
        Instant lastAddedAt,
        String lastAddedBy
) {}
