package app.lightmove.api.candidate.model;

import app.lightmove.api.common.constant.CompetencyPanel;
import java.util.List;
import org.springframework.modulith.NamedInterface;

/**
 * One panel's criteria and totals.
 *
 * @param share   the panel's part of the assessment's 100
 * @param average the mean of the scores given, null when none is
 */
@NamedInterface(name = "api", propagate = false)
public record ScorecardPanelTally(
        CompetencyPanel panel,
        int share,
        double points,
        Double average,
        int scoredCount,
        List<ScoredCriterion> criteria
) {}
