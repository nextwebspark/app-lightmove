package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Erase a person for good. {@code confirmName} is their full name as the admin typed it, case and outer spaces
 * forgiven. {@code suppress}, on unless sent false, puts their profile and every address on the suppression list so
 * no door files them again.
 */
public record ErasePersonRequest(
        @NotBlank @Size(max = 200) String confirmName,
        Boolean suppress
) {}
