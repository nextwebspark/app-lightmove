package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.ScorecardStatus;
import java.time.Instant;
import org.springframework.modulith.NamedInterface;

/**
 * One assessed executive of a position as the Ranking chapter reads them: who they are, and their assessment tallied
 * against the brief as it stands — the same numbers the drawer's Assessment tab shows.
 */
@NamedInterface(name = "api", propagate = false)
public record ScorecardStanding(
        RankedPerson person,
        ScorecardStatus status,
        Instant updatedAt,
        Instant reviewedAt,
        String reviewedByName,
        ScorecardTally tally
) {}
