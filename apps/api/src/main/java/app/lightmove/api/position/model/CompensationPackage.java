package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.BaseSalaryMode;
import app.lightmove.api.position.constant.BonusBasis;
import app.lightmove.api.position.constant.IncentiveType;
import java.math.BigDecimal;
import java.util.List;

/**
 * Step four of the brief: what the seat pays. Every figure carries the unit it is quoted in — the
 * base its period, the bonus its basis, each allowance its frequency — because a package assembled
 * from bare numbers cannot be annualised, and annualising it is the only thing anyone does with it.
 */
public record CompensationPackage(
        String currency,
        Long salaryMin,
        Long salaryMax,
        BaseSalaryMode baseSalaryMode,
        BigDecimal bonusValue,
        BonusBasis bonusBasis,
        IncentiveType incentiveType,
        Long incentiveAmount,
        String incentiveVesting,
        List<PositionBenefit> benefits
) {
}
