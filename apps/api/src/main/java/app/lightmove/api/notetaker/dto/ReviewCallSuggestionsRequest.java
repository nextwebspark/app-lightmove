package app.lightmove.api.notetaker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The researcher's review of a call's waiting suggestions: those in {@code apply} are applied, every other one skipped. */
public record ReviewCallSuggestionsRequest(
        @NotNull @Size(max = 50) List<@NotNull @Valid ApplyCallSuggestionRequest> apply
) {}
