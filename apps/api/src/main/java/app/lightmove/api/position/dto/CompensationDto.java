package app.lightmove.api.position.dto;

import app.lightmove.api.common.constant.BaseSalaryMode;
import app.lightmove.api.common.constant.BonusBasis;
import app.lightmove.api.common.constant.IncentiveType;
import app.lightmove.api.position.constant.FieldSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
        List<BenefitDto> benefits,
        /** Provenance of the scalars above; each benefit carries its own {@code source}. */
        Map<String, FieldSource> fieldSources
) {}
