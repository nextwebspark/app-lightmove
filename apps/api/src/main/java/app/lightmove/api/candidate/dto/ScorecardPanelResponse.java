package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.model.ScorecardPanelTally;
import java.util.List;

public record ScorecardPanelResponse(
        String panel,
        int share,
        double points,
        Double average,
        int scoredCount,
        List<ScorecardCriterionResponse> criteria
) {

    static ScorecardPanelResponse of(ScorecardPanelTally panel) {
        return new ScorecardPanelResponse(panel.panel().value(), panel.share(),
                panel.points(), panel.average(), panel.scoredCount(),
                panel.criteria().stream().map(ScorecardCriterionResponse::of).toList());
    }
}
