package app.lightmove.api.strategy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * The filter autosave. One request for the whole sidebar rather than one per accordion: the screen
 * holds the entire selection and a chip click is a snapshot of it, so a partial PUT would only
 * create a way for two accordions to disagree about what is selected.
 */
public record PutStrategyFilterRequest(
        @NotNull(message = "A filter is required")
        @Valid
        StrategyFilterDto filter
) {}
