package app.lightmove.api.strategy.dto;

import app.lightmove.api.strategy.constant.SearchVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Editing a saved search's label and tier. Its filter is not here: re-capturing the mandate's current
 * filter onto a search is a separate, explicit act, so that renaming one can never silently move the
 * scope it stands for.
 *
 * <p>A missing {@code visibility} leaves the tier as it is.
 */
public record UpdateSearchRequest(
        @NotBlank(message = "A name is required")
        @Size(max = 120, message = "A name must be 120 characters or fewer")
        String name,

        SearchVisibility visibility
) {}
