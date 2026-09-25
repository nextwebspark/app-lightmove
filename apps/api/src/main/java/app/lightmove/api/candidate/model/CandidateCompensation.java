package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

import app.lightmove.api.candidate.constant.LongTermIncentiveType;
import java.util.List;
import java.util.Objects;

/**
 * What an executive is paid today, as a researcher records it: the four elements a GCC package is
 * quoted in, plus how long it takes to move them.
 *
 * <p>One currency for the whole package and no conversion — a rate applied at write time is wrong by
 * the time anyone reads the row. Every element is nullable and none is interchangeable with zero:
 * "no bonus" is a fact about the package, "not established yet" a fact about the research.
 *
 * <p>{@code allowances} is the total every reader sums and the breakdown only itemises it, so the two
 * are reconciled here and nowhere else: lines with no total supply it, and a total that contradicts
 * its lines — a re-imported cell, say — is the later statement and drops them. An LTIP amount above
 * nought likewise contradicts a recorded "None", which is dropped; instruments with no amount stay, the
 * figure being not yet established rather than nought.
 */
public record CandidateCompensation(String currency, Long baseSalary, Long bonus, Long allowances,
                                    Long longTermIncentive, String noticePeriod,
                                    CompensationBreakdown breakdown) {

    public CandidateCompensation {
        String supplied = blankToNull(currency);
        currency = supplied == null ? null : supplied.toUpperCase();
        noticePeriod = blankToNull(noticePeriod);
        breakdown = breakdown == null ? CompensationBreakdown.empty() : breakdown;
        Long itemised = breakdown.allowanceTotal();
        if (itemised != null && allowances == null) {
            allowances = itemised;
        } else if (itemised != null && !Objects.equals(itemised, allowances)) {
            breakdown = breakdown.withoutAllowanceLines();
        }
        if (longTermIncentive != null && longTermIncentive > 0
                && breakdown.longTermIncentiveTypes().equals(List.of(LongTermIncentiveType.NONE))) {
            breakdown = new CompensationBreakdown(breakdown.allowanceLines(), List.of());
        }
    }

    /** What a candidate carries before anyone has established a package. */
    public static CandidateCompensation unknown() {
        return new CandidateCompensation(null, null, null, null, null, null, CompensationBreakdown.empty());
    }
}
