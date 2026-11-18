package app.lightmove.api.candidate.model;

import java.util.List;
import org.springframework.modulith.NamedInterface;

/**
 * An assessment read against the brief as it stands today.
 *
 * @param retired scores given against criteria the brief no longer has: kept and shown, never counted
 */
@NamedInterface(name = "api", propagate = false)
public record ScorecardTally(
        double overall,
        int scoredCount,
        int criteriaCount,
        List<ScorecardPanelTally> panels,
        List<CriterionScore> retired
) {}
