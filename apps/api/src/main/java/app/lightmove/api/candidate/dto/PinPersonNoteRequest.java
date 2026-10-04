package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotNull;

public record PinPersonNoteRequest(@NotNull Boolean pinned) {}
