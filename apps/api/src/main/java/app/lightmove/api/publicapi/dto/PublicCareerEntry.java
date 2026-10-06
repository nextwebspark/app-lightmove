package app.lightmove.api.publicapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CareerEntry", description = "One post in an executive's career")
public record PublicCareerEntry(
        @Schema(description = "The employer", nullable = true, example = "Saudi Aramco") String company,
        @Schema(description = "The title held", nullable = true, example = "VP Finance") String title,
        @Schema(description = "When, as the source wrote it", nullable = true, example = "2019–Present") String period,
        @Schema(description = "Where the post was held", nullable = true, example = "Dhahran") String location
) {}
