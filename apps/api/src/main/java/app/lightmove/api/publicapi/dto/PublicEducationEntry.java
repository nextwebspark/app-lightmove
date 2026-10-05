package app.lightmove.api.publicapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EducationEntry", description = "One school in an executive's education")
public record PublicEducationEntry(
        @Schema(description = "The school", nullable = true, example = "INSEAD") String school,
        @Schema(description = "The degree", nullable = true, example = "MBA") String degree,
        @Schema(description = "When, as the source wrote it", nullable = true, example = "2010–2011") String period
) {}
