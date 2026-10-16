package app.uncava.api.position.dto;

import app.uncava.api.common.constant.BaseSalaryMode;
import app.uncava.api.common.constant.BonusBasis;
import app.uncava.api.common.constant.IncentiveType;
import java.math.BigDecimal;
import java.util.List;

/** Step four as the brief returns it. Every figure travels with the unit it is quoted in. */
public record CompensationDto(
        String currency,
        Long salaryMin,
        Long salaryMax,
        BaseSalaryMode baseSalaryMode,
        BigDecimal bonusValue,
        BonusBasis bonusBasis,
        IncentiveType incentiveType,
        Long incentiveAmount,
        String incentiveVesting,
        List<BenefitDto> benefits
) {}
