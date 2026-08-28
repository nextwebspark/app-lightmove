package app.lightmove.api.position.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One strategic priority chip — the same shape reads and writes. */
public record StrategicPriorityDto(
        @NotBlank(message = "Name the priority")
        @Size(max = 120, message = "That priority is too long")
        String name,

        boolean selected
) {}
