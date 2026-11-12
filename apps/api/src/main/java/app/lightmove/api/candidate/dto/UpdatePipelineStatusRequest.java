package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.model.CandidateStatusDefinition;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * An admin's change to a status; each field left null is left as it is. {@code stageId} moves it to the
 * end of another stage, {@code isDefault: true} makes it its stage's default, {@code outOfProcess} flags it as a
 * way out of the process.
 */
public record UpdatePipelineStatusRequest(
        @Size(min = 1, max = CandidateStatusDefinition.MAX_LABEL) String label,
        String colour,
        UUID stageId,
        Boolean isDefault,
        Boolean outOfProcess,
        Boolean retired
) {}
