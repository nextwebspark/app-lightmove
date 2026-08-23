package app.lightmove.api.strategy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Saving the current filter under a name. The filter itself is not in the request: it is read from
 * the strategy the mandate has already autosaved, so what gets saved is exactly what is on screen
 * and the two cannot drift.
 */
public record SaveSearchRequest(
        @NotBlank(message = "A name is required")
        @Size(max = 120, message = "A name must be 120 characters or fewer")
        String name
) {}
