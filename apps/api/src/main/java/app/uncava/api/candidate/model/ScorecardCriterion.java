package app.uncava.api.candidate.model;

import app.uncava.api.common.constant.CompetencyPanel;

/** One competency of the position's brief, as the assessment scores against it. */
public record ScorecardCriterion(CompetencyPanel panel, String name, String description, int weight) {

    public String matchKey() {
        return CriterionScore.keyOf(panel, name);
    }
}
