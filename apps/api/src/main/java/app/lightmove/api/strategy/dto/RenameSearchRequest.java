package app.lightmove.api.strategy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Renaming a saved search. Its filter is fixed at save time and is not editable in place. */
public record RenameSearchRequest(
        @NotBlank(message = "A name is required")
        @Size(max = 120, message = "A name must be 120 characters or fewer")
        String name
) {}
