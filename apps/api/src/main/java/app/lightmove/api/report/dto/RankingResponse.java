package app.lightmove.api.report.dto;

import java.util.List;

/**
 * The Ranking chapter: the brief's competencies and weighting, every assessed executive's scores against them, and
 * the position's scenario. Staff-only. The order is the screen's to compute — it moves with the scenario — so the
 * candidates arrive best-scored first at the baseline, nothing more.
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
