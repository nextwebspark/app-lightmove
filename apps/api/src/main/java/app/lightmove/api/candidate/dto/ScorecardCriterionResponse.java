package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.model.CriterionScore;
import app.lightmove.api.candidate.model.ScoredCriterion;

/** One criterion as the Assessment tab draws it; a retired one carries no description and counts for nothing. */
public record ScorecardCriterionResponse(
        String panel,
        String name,
        String description,
        int weight,
        double weightInPanel,
        double maxPoints,
        Integer score,
        double points,
        String evidence
) {

    static ScorecardCriterionResponse of(ScoredCriterion criterion) {
        return new ScorecardCriterionResponse(criterion.panel().value(), criterion.name(),
                criterion.description(), criterion.weight(), criterion.weightInPanel(), criterion.maxPoints(),
                criterion.score(), criterion.points(), criterion.evidence());
    }

    static ScorecardCriterionResponse retired(CriterionScore score) {
        return new ScorecardCriterionResponse(score.panel().value(), score.name(), null, score.weight(), 0, 0,
                score.score(), 0, score.evidence());
    }
}
