package app.lightmove.api.candidate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A package as the drawer edits it, nested so the currency stays with its figures. No upper bound (weak currencies
 * quote large figures); only negatives are refused. The lists itemise {@code allowances} and {@code longTermIncentive}.
 */
public record CandidateCompensationDto(
        @Size(max = 3, message = "Use a three-letter currency code")
        String currency,

        @PositiveOrZero(message = "A base salary cannot be negative")
        Long baseSalary,

        @PositiveOrZero(message = "A bonus cannot be negative")
        Long bonus,

        @PositiveOrZero(message = "Allowances cannot be negative")
        Long allowances,

        @PositiveOrZero(message = "A long-term incentive cannot be negative")
        Long longTermIncentive,

        @Size(max = 100)
        String noticePeriod,

        @Valid
        @Size(max = 12, message = "A package lists 12 allowances at most")
        List<AllowanceLineDto> allowanceLines,

        @Size(max = 4)
        List<String> longTermIncentiveTypes
) {}
