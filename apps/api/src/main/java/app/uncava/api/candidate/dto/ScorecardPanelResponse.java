package app.uncava.api.candidate.dto;

import app.uncava.api.candidate.model.ScorecardPanelTally;
import java.util.List;
import java.util.Set;

public record ScorecardPanelResponse(
        String panel,
        int share,
        double points,
        Double average,
        int scoredCount,
        List<ScorecardCriterionResponse> criteria
) {

    static ScorecardPanelResponse of(ScorecardPanelTally panel, Set<String> aiDrafted) {
        return new ScorecardPanelResponse(panel.panel().value(), panel.share(),
                panel.points(), panel.average(), panel.scoredCount(),
                panel.criteria().stream()
                        .map(criterion -> ScorecardCriterionResponse.of(criterion, aiDrafted))
                        .toList());
    }
}
