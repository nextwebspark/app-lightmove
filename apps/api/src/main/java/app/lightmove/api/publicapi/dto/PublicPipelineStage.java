package app.lightmove.api.publicapi.dto;

import app.lightmove.api.candidate.dto.CandidatePipelineStageDto;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PipelineStage", description = "The stage of the workspace's pipeline an executive's status sits under")
public record PublicPipelineStage(
        @Schema(description = "The stage's key, stable across renames", example = "contacted") String key,
        @Schema(description = "The stage's name as the workspace spells it", example = "Contacted") String label,
        @Schema(description = "Whether the stage keeps someone in the running; closed means they have left it",
                allowableValues = {"active", "closed"}, example = "active")
        String kind
) {

    public static PublicPipelineStage of(CandidatePipelineStageDto stage) {
        return stage == null ? null : new PublicPipelineStage(stage.key(), stage.label(), stage.kind());
    }
}
