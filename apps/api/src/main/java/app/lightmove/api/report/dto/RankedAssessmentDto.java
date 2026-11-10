package app.lightmove.api.report.dto;

import java.time.Instant;
import java.util.List;

/**
 * An executive's assessment as the ranking reads it.
 *
 * @param status          {@code draft} or {@code reviewed}
 * @param baselineOverall out of 100 at the brief's weighting, rounded as the drawer's Assessment tab rounds it
 * @param retiredCount    scores held against competencies the brief no longer has, never counted
 */
public record RankedAssessmentDto(
        String status,
        Instant updatedAt,
        Instant reviewedAt,
        String reviewedByName,
        double baselineOverall,
        int scoredCount,
        int criteriaCount,
        int retiredCount,
        List<RankedScoreDto> scores
) {}
