package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.AssertTrue;
import java.util.UUID;

/** Where a dragged row lands: between these two neighbours of its own stage, either null at an end. */
public record RankPipelineRowRequest(UUID afterCandidateId, UUID beforeCandidateId) {

    @AssertTrue(message = "A row lands between two different neighbours")
    public boolean isBetweenDistinctNeighbours() {
        return afterCandidateId == null || !afterCandidateId.equals(beforeCandidateId);
    }
}
