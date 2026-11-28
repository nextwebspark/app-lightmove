package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.model.MoveExpectations;
import app.lightmove.api.core.validation.SuppliedCurrencyCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The Compensation section as the drawer edits it, nested so the currency stays with its figures: the current package,
 * then what a move would take, its expected figures in the same currency. No upper bound (weak currencies quote large
 * figures); only negatives are refused. The lists itemise {@code allowances} and {@code longTermIncentive}.
 */
public record CandidateCompensationDto(
        @SuppliedCurrencyCode
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
        List<String> longTermIncentiveTypes,

        @PositiveOrZero(message = "An expected base cannot be negative")
        Long expectedBaseSalary,

        @PositiveOrZero(message = "An expected package cannot be negative")
        Long expectedTotalPackage,

        @Size(max = MoveExpectations.LONGEST_NOTE, message = "Keep it to {max} characters")
        String expectedPackageNote,

        @Size(max = MoveExpectations.LONGEST_AVAILABLE_FROM, message = "Keep it to {max} characters")
        String availableFrom,

        /** A {@code Mobility} wire token. Omitted or blank is not established, which is not "none". */
        @Size(max = 16)
        String mobility,

        @Size(max = MoveExpectations.LONGEST_NOTE, message = "Keep it to {max} characters")
        String mobilityNote
) {}
