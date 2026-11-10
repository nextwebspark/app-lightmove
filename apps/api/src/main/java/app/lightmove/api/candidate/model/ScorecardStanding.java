package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.ScorecardStatus;
import app.lightmove.api.candidate.dto.CandidateResponse;
import java.time.Instant;
import java.util.UUID;

/**
 * One assessed executive of a position as the Ranking chapter reads them: who they are, and their assessment tallied
 * against the brief as it stands — the same numbers the drawer's Assessment tab shows.
 *
 * @param processingRestricted their processing is restricted (V198): still shown to staff, never sent to a model
 */
public record ScorecardStanding(
        CandidateResponse candidate,
        ScorecardStatus status,
        Instant updatedAt,
        Instant reviewedAt,
        String reviewedByName,
        ScorecardTally tally,
        boolean processingRestricted
) {

    public UUID candidateId() {
        return candidate.id();
    }
}
