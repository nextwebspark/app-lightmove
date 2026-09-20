package app.lightmove.api.report.service;

import app.lightmove.api.candidate.dto.CandidateCompensationDto;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.common.constant.BaseSalaryMode;
import app.lightmove.api.common.constant.BonusBasis;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.position.dto.CompensationDto;
import app.lightmove.api.report.dto.CompensationBandDto;
import app.lightmove.api.report.dto.DisclosureDto;
import app.lightmove.api.report.dto.RemunerationDto;
import app.lightmove.api.report.model.ExecutiveRow;
import app.lightmove.api.report.model.ReportSources;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Chapter three. The brief's band is annualised and widened into a package band the way the brief
 * itself quotes it — bonus on its basis, the incentive as stated — and set beside every executive
 * who disclosed a base salary in the same currency. No conversion: a figure in another currency is
 * counted out loud rather than converted at a rate nobody chose.
 */
@Component
class RemunerationReporter {

    private static final int MONTHS_PER_YEAR = 12;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    RemunerationDto report(ReportSources sources) {
        CompensationDto brief = sources.compensation();
        CompensationBandDto fixedBand = fixedBandOf(brief);
        CompensationBandDto packageBand = fixedBand == null ? null : packageBandOf(brief, fixedBand);

        List<DisclosureDto> disclosures = new ArrayList<>();
        int otherCurrency = 0;
        for (ExecutiveRow row : sources.executives()) {
            CandidateResponse executive = row.executive();
            if (!Packages.isDisclosed(executive.compensation())) {
                continue;
            }
            if (!Packages.isInCurrency(executive.compensation(), brief.currency())) {
                otherCurrency++;
                continue;
            }
            disclosures.add(disclosure(row));
        }
        return new RemunerationDto(brief.currency(), fixedBand, packageBand, disclosures, otherCurrency);
    }

    static CompensationBandDto fixedBandOf(CompensationDto brief) {
        if (brief.salaryMin() == null && brief.salaryMax() == null) {
            return null;
        }
        long low = annual(brief.salaryMin() != null ? brief.salaryMin() : brief.salaryMax(), brief.baseSalaryMode());
        long high = annual(brief.salaryMax() != null ? brief.salaryMax() : brief.salaryMin(), brief.baseSalaryMode());
        return new CompensationBandDto(Math.min(low, high), Math.max(low, high));
    }

    static CompensationBandDto packageBandOf(CompensationDto brief, CompensationBandDto fixedBand) {
        long incentive = brief.incentiveAmount() == null ? 0 : brief.incentiveAmount();
        return new CompensationBandDto(
                fixedBand.low() + bonusOn(fixedBand.low(), brief) + incentive,
                fixedBand.high() + bonusOn(fixedBand.high(), brief) + incentive);
    }

    private static long annual(long figure, BaseSalaryMode mode) {
        return mode == BaseSalaryMode.MONTHLY ? figure * MONTHS_PER_YEAR : figure;
    }

    /**
     * The target bonus on one edge of the band, in the unit the brief quoted it: a share of base,
     * months of it, or a stated amount, which is the same on both edges.
     *
     * <p>A share of <i>total fixed</i> is computed on base alone, because the brief states no allowance
     * figure to add — its benefits are a list of named items, not a sum. The day the brief carries one,
     * {@code PERCENT_OF_TOTAL_FIXED} needs its own arm here or it under-states the package.
     */
    private static long bonusOn(long annualBase, CompensationDto brief) {
        BigDecimal value = brief.bonusValue();
        BonusBasis basis = brief.bonusBasis();
        if (value == null || basis == null) {
            return 0;
        }
        BigDecimal base = BigDecimal.valueOf(annualBase);
        BigDecimal bonus = switch (basis) {
            case PERCENT_OF_BASE, PERCENT_OF_TOTAL_FIXED -> base.multiply(value).divide(HUNDRED, RoundingMode.HALF_UP);
            case MONTHS_OF_BASE -> base.multiply(value).divide(BigDecimal.valueOf(MONTHS_PER_YEAR), RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> value;
        };
        return bonus.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static DisclosureDto disclosure(ExecutiveRow row) {
        CandidateResponse executive = row.executive();
        CandidateCompensationDto compensation = executive.compensation();
        return new DisclosureDto(executive.id(), executive.fullName(), row.employerName(), executive.title(),
                Countries.nameOf(executive.locationCountry()), NationalityCatalog.groupOf(executive.nationality()),
                executive.status(), Packages.fixedOf(compensation), Packages.totalOf(compensation),
                executive.note());
    }
}
