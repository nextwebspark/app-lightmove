package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** One named allowance as the drawer edits it. */
public record AllowanceLineDto(
        @Size(max = 60, message = "An allowance name is 60 characters at most")
        String label,

        @PositiveOrZero(message = "An allowance cannot be negative")
        Long amount
) {}
