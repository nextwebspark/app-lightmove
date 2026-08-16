package app.lightmove.api.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One weighted competency — the same shape reads and writes. */
public record CompetencyDto(
        @NotBlank(message = "Name the competency")
        @Size(max = 120, message = "That name is too long")
        String name,

        @Min(value = 0, message = "Weights are between 0 and 100")
        @Max(value = 100, message = "Weights are between 0 and 100")
        int weight
) {}
