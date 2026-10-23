package app.lightmove.api.notetaker.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** A suggestion's value as the researcher edited it in the review; only the parts its kind reads are used. */
public record EditedSuggestionValue(
        @Size(max = 1000, message = "Keep it under 1,000 characters") String text,
        @Size(max = 120, message = "Keep the city under 120 characters") String city,
        @Size(max = 100) String country,
        @Size(max = 3, message = "Use a three-letter currency code") String currency,
        @PositiveOrZero(message = "A base salary cannot be negative") Long baseSalary,
        @PositiveOrZero(message = "A bonus cannot be negative") Long bonus,
        @PositiveOrZero(message = "Allowances cannot be negative") Long allowances
) {}
