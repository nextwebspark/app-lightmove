package app.lightmove.api.gettingstarted.dto;

import jakarta.validation.constraints.NotNull;

public record SkipGettingStartedStepRequest(@NotNull Boolean skipped) {}
