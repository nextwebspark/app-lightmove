package app.lightmove.api.triagecompany.dto;

import jakarta.validation.constraints.NotNull;

public record PinCompanyNoteRequest(@NotNull Boolean pinned) {}
