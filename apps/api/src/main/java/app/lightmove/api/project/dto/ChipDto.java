package app.lightmove.api.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One chip: a sector or tag label and whether it is currently in scope. */
public record ChipDto(
        @NotBlank(message = "Every chip needs a label")
        @Size(max = 160, message = "That chip label is too long")
        String label,

        boolean selected
) {}
