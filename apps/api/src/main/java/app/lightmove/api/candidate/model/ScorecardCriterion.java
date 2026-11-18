package app.lightmove.api.candidate.model;

import app.lightmove.api.common.constant.CompetencyPanel;
import org.springframework.modulith.NamedInterface;

/** One competency of the position's brief, as the assessment scores against it. */
@NamedInterface(name = "api", propagate = false)
public record ScorecardCriterion(CompetencyPanel panel, String name, String description, int weight) {

    public String matchKey() {
        return CriterionScore.keyOf(panel, name);
    }
}
