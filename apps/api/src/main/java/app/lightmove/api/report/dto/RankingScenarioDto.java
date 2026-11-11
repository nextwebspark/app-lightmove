package app.lightmove.api.report.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The position's saved ranking scenario, its weights laid onto the brief as it stands today.
 *
 * @param weights                competency key → weight, every competency of today's brief
 * @param briefChangedSinceSaved the brief's competencies, weights or share moved after the save
 * @param droppedCriteria        competencies the scenario weighed that the brief has since dropped
 */
public record RankingScenarioDto(
        long version,
        int technicalShare,
        Map<String, Integer> weights,
        String rationale,
        Instant updatedAt,
        String updatedByName,
        boolean briefChangedSinceSaved,
        List<String> droppedCriteria
) {}
