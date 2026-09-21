package app.lightmove.api.position.dto;

import app.lightmove.api.position.constant.FieldSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One line of a brief's responsibilities — the same shape reads and writes. */
public record ResponsibilityDto(
        @NotBlank(message = "Enter the responsibility")
        @Size(max = 200, message = "That responsibility is too long")
        String text,

        /** Null on a write defaults to {@code MANUAL} — a person typed it. */
        FieldSource source
) {}
