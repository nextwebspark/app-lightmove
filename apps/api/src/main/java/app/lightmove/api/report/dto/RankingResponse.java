package app.lightmove.api.report.dto;

import java.util.List;

/**
 * The staff-only Ranking chapter; candidates arrive best-scored first at the baseline, the screen computes the order.
 *
 * @param notAssessedCount mapped executives with no score against a current competency
 * @param truncated        more were assessed than {@code lightmove.report.max-ranked} lets through
 * @param scenario         null when the position ranks at the brief's own weighting
 */
public record RankingResponse(
        List<RankingCriterionDto> criteria,
        int baselineTechnicalShare,
        List<RankedCandidateDto> candidates,
        long notAssessedCount,
        long mappedCount,
        boolean truncated,
        RankingScenarioDto scenario
) {}
