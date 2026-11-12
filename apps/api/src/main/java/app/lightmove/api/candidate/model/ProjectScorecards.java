package app.lightmove.api.candidate.model;

import java.util.List;

/**
 * One position's assessed executives for {@code report}'s Ranking; one with no current competency scored counts as unassessed.
 * @param standings the best-scored first, cut at the caller's cap
 * @param assessedCount everyone holding a score against a current competency, the cap aside
 * @param mappedCount everyone the position maps, assessed or not
 */
public record ProjectScorecards(
        List<ScorecardCriterion> criteria,
        int technicalShare,
        List<ScorecardStanding> standings,
        long assessedCount,
        long mappedCount
) {

    public boolean truncated() {
        return assessedCount > standings.size();
    }
}
