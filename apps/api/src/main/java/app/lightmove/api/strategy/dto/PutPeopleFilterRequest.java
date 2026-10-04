package app.lightmove.api.strategy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** The whole People sidebar in one autosave, as {@link PutStrategyFilterRequest} is for companies. */
public record PutPeopleFilterRequest(
        @NotNull(message = "A filter is required")
        @Valid
        PeopleFilterDto filter
) {}
