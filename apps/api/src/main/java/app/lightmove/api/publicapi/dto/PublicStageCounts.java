package app.lightmove.api.publicapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "StageCounts", description = "How many companies a position has filed at each stage")
public record PublicStageCounts(
        @Schema(description = "Companies in the universe", example = "48") long inUniverse,
        @Schema(description = "Companies shortlisted", example = "12") long shortlisted,
        @Schema(description = "Companies declined", example = "7") long declined
) {}
