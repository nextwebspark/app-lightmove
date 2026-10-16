package app.uncava.api.candidate.dto;

import app.uncava.api.candidate.model.CandidateStatusDefinition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** A new status, last in its stage — a status is never made without one. {@code colour} defaults to the stage's. */
public record CreatePipelineStatusRequest(
        @NotNull UUID stageId,
        @NotBlank @Size(max = CandidateStatusDefinition.MAX_LABEL) String label,
        String colour
) {}
