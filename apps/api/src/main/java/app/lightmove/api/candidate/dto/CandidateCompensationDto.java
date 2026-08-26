package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * A package as the drawer edits it and the API answers it. Nested rather than flattened into twenty
 * sibling fields, because the currency is only meaningful attached to the numbers it qualifies.
 *
 * <p>No upper bound on the elements: executive packages in this market are quoted in whole units of
 * currencies worth a fraction of a dollar, and a ceiling picked for AED would refuse a legitimate
 * figure in another. Negative is refused, which is the only claim that is never true of a salary.
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
        String noticePeriod
) {}
