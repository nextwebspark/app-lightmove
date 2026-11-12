package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.model.CandidateStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A new stage, placed after the live ones. {@code colour} defaults to neutral. */
public record CreatePipelineStageRequest(
        @NotBlank @Size(max = CandidateStage.MAX_LABEL) String label,
        String colour
) {}
