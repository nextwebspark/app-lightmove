package app.lightmove.api.candidate.model;

import java.util.List;

/**
 * Every assessed executive of one position, against its brief's competencies — the seam {@code report}'s Ranking
 * chapter reads. An assessment with no current competency scored is not one: it is counted with the unassessed.
 *
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
