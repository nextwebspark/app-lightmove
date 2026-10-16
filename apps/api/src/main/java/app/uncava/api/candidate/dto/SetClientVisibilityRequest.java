package app.uncava.api.candidate.dto;

import jakarta.validation.constraints.NotNull;

/** Whether a client seat on the position sees this executive at all. */
public record SetClientVisibilityRequest(@NotNull Boolean clientVisible) {}
