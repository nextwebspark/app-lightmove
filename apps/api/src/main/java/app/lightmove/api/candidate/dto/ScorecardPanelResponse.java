package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.model.ScorecardPanelTally;
import java.util.List;

public record ScorecardPanelResponse(
        String panel,
        int share,
        double maxPoints,
        double points,
        Double average,
        int scoredCount,
        List<ScorecardCriterionResponse> criteria
) {

    static ScorecardPanelResponse of(ScorecardPanelTally panel) {
        return new ScorecardPanelResponse(ScorecardCriterionResponse.panelOf(panel.panel().name()), panel.share(),
                panel.maxPoints(), panel.points(), panel.average(), panel.scoredCount(),
                panel.criteria().stream().map(ScorecardCriterionResponse::of).toList());
    }
}
