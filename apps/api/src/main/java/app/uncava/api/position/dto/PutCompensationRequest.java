package app.uncava.api.position.dto;

import app.uncava.api.common.constant.BaseSalaryMode;
import app.uncava.api.common.constant.BonusBasis;
import app.uncava.api.common.constant.IncentiveType;
import app.uncava.api.core.validation.CurrencyCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Snapshot PUT of step four. No minimum-below-maximum rule: autosave has to persist a band while it
 * is still being typed, and the screen is what reads a half-entered one back to the consultant.
 */
public record PutCompensationRequest(
        @NotNull(message = "Choose a currency")
        @CurrencyCode(message = "Choose a currency from the list")
        String currency,

        @Min(value = 0, message = "Salary cannot be negative") Long salaryMin,
        @Min(value = 0, message = "Salary cannot be negative") Long salaryMax,

        @NotNull(message = "Say whether the band is annual or monthly")
        BaseSalaryMode baseSalaryMode,

        @DecimalMin(value = "0", message = "A bonus cannot be negative")
        @Digits(integer = 12, fraction = 2, message = "That bonus figure is too precise")
        BigDecimal bonusValue,
        BonusBasis bonusBasis,

        IncentiveType incentiveType,
        @Min(value = 0, message = "An incentive cannot be negative") Long incentiveAmount,
        @Size(max = 200, message = "That vesting schedule is too long") String incentiveVesting,

        @Size(max = 20, message = "That is too many benefits")
        List<@Valid BenefitDto> benefits
) {}
