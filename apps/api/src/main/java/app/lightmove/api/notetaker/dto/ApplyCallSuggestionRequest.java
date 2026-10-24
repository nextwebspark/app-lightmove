package app.lightmove.api.notetaker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Apply a suggestion as it was made, or as the researcher edited it ({@code value}). For a package,
 * {@code packageFields} names which of {@code baseSalary}, {@code bonus} and {@code allowances} to take; omitted, every
 * figure the suggestion holds.
 */
public record ApplyCallSuggestionRequest(
        @Valid EditedSuggestionValue value,
        @Size(max = 3) List<String> packageFields
) {}
