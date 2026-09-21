package app.lightmove.api.position.dto;

import app.lightmove.api.position.constant.FieldSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One strategic priority chip — the same shape reads and writes. */
public record StrategicPriorityDto(
        @NotBlank(message = "Name the priority")
        @Size(max = 120, message = "That priority is too long")
        String name,

        boolean selected,

        /** Null on a write defaults to {@code MANUAL} — a person typed it. */
        FieldSource source
) {}
