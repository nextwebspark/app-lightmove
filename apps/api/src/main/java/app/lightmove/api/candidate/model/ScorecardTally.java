package app.lightmove.api.candidate.model;

import java.util.List;

/**
 * An assessment read against the brief as it stands today.
 *
 * @param retired scores given against criteria the brief no longer has: kept and shown, never counted
 */
public record ScorecardTally(
        double overall,
        int scoredCount,
        int criteriaCount,
        List<ScorecardPanelTally> panels,
        List<CriterionScore> retired
) {}
