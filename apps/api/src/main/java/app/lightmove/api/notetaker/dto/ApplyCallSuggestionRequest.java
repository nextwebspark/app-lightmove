package app.lightmove.api.notetaker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * One suggestion the review applies, as it was made or as the researcher edited it ({@code value}). For a package,
 * {@code packageFields} names which of {@code baseSalary}, {@code bonus} and {@code allowances} to take; omitted, every
 * figure the suggestion holds.
 */
public record ApplyCallSuggestionRequest(
        @NotBlank @Size(max = 64) String id,
        @Valid EditedSuggestionValue value,
        @Size(max = 3) List<String> packageFields
) {}
