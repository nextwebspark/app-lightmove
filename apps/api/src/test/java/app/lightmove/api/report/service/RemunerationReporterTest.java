package app.lightmove.api.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.common.constant.BaseSalaryMode;
import app.lightmove.api.common.constant.BonusBasis;
import app.lightmove.api.common.constant.IncentiveType;
import app.lightmove.api.position.dto.CompensationDto;
import app.lightmove.api.report.dto.CompensationBandDto;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The brief's band as the report states it: annual, and widened by the bonus and incentive it quotes. */
class RemunerationReporterTest {

    @Test
    @DisplayName("a monthly band is annualised, and a percentage bonus plus the incentive widen it into a package")
    void monthlyBandBecomesAnAnnualPackage() {
        CompensationDto brief = brief(20_000L, 25_000L, BaseSalaryMode.MONTHLY,
                BigDecimal.valueOf(20), BonusBasis.PERCENT_OF_BASE, 100_000L);

        CompensationBandDto fixed = RemunerationReporter.fixedBandOf(brief);
        CompensationBandDto totalPackage = RemunerationReporter.packageBandOf(brief, fixed);

        assertThat(fixed).isEqualTo(new CompensationBandDto(240_000, 300_000));
        assertThat(totalPackage).isEqualTo(new CompensationBandDto(388_000, 460_000));
    }

    @Test
    @DisplayName("a bonus quoted in months of base is that many twelfths")
    void monthsOfBaseBonus() {
        CompensationDto brief = brief(120_000L, 120_000L, BaseSalaryMode.ANNUAL,
                BigDecimal.valueOf(3), BonusBasis.MONTHS_OF_BASE, null);

        CompensationBandDto totalPackage = RemunerationReporter.packageBandOf(brief,
                RemunerationReporter.fixedBandOf(brief));

        assertThat(totalPackage).isEqualTo(new CompensationBandDto(150_000, 150_000));
    }

    @Test
    @DisplayName("a bonus quoted on total fixed is taken on base, the only fixed figure the brief states")
    void percentOfTotalFixedIsTakenOnBase() {
        CompensationDto onBase = brief(200_000L, 200_000L, BaseSalaryMode.ANNUAL,
                BigDecimal.valueOf(25), BonusBasis.PERCENT_OF_BASE, null);
        CompensationDto onTotalFixed = brief(200_000L, 200_000L, BaseSalaryMode.ANNUAL,
                BigDecimal.valueOf(25), BonusBasis.PERCENT_OF_TOTAL_FIXED, null);

        assertThat(RemunerationReporter.packageBandOf(onTotalFixed, RemunerationReporter.fixedBandOf(onTotalFixed)))
                .isEqualTo(RemunerationReporter.packageBandOf(onBase, RemunerationReporter.fixedBandOf(onBase)))
                .isEqualTo(new CompensationBandDto(250_000, 250_000));
    }

    @Test
    @DisplayName("a band with one edge is that edge twice, and a brief with none states no band")
    void partialAndAbsentBands() {
        assertThat(RemunerationReporter.fixedBandOf(brief(null, 90_000L, BaseSalaryMode.ANNUAL, null, null, null)))
                .isEqualTo(new CompensationBandDto(90_000, 90_000));
        assertThat(RemunerationReporter.fixedBandOf(brief(null, null, BaseSalaryMode.ANNUAL, null, null, null)))
                .isNull();
    }

    private static CompensationDto brief(Long salaryMin, Long salaryMax, BaseSalaryMode mode, BigDecimal bonusValue,
                                         BonusBasis bonusBasis, Long incentiveAmount) {
        return new CompensationDto("USD", salaryMin, salaryMax, mode, bonusValue, bonusBasis,
                incentiveAmount == null ? null : IncentiveType.LTIP_CASH, incentiveAmount, null, List.of());
    }
}
