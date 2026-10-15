package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.model.CriterionScore;
import app.lightmove.api.candidate.model.ScoredCriterion;
import java.util.Set;

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
        String evidence,
        boolean aiDrafted
) {

    static ScorecardCriterionResponse of(ScoredCriterion criterion, Set<String> aiDrafted) {
        return new ScorecardCriterionResponse(criterion.panel().value(), criterion.name(),
                criterion.description(), criterion.weight(), criterion.weightInPanel(), criterion.maxPoints(),
                criterion.score(), criterion.points(), criterion.evidence(),
                aiDrafted.contains(CriterionScore.keyOf(criterion.panel(), criterion.name())));
    }

    static ScorecardCriterionResponse retired(CriterionScore score, Set<String> aiDrafted) {
        return new ScorecardCriterionResponse(score.panel().value(), score.name(), null, score.weight(), 0, 0,
                score.score(), 0, score.evidence(), aiDrafted.contains(score.matchKey()));
    }
}
