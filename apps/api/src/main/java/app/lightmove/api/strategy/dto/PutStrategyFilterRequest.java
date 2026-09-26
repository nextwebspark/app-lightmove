package app.lightmove.api.strategy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** The whole sidebar in one autosave, so two accordions cannot disagree about the selection. */
public record PutStrategyFilterRequest(
        @NotNull(message = "A filter is required")
        @Valid
        StrategyFilterDto filter
) {}
