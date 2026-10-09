package app.lightmove.api.gettingstarted.dto;

import jakarta.validation.constraints.NotNull;

public record DismissGettingStartedRequest(@NotNull Boolean dismissed) {}
