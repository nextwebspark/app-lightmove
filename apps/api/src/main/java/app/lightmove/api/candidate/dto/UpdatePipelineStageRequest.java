package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.model.CandidateStage;
import jakarta.validation.constraints.Size;

/** An admin's change to a stage; each field left null is left as it is. */
public record UpdatePipelineStageRequest(
        @Size(min = 1, max = CandidateStage.MAX_LABEL) String label,
        String colour,
        Boolean retired
) {}
