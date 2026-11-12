package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

import app.lightmove.api.candidate.constant.LongTermIncentiveType;
import java.util.List;
import java.util.Objects;

/**
 * An executive's current package: one currency, never converted, and null is never zero. {@code allowances} is the
 * total the breakdown itemises, reconciled here alone: lines supply a missing total, a contradicting total drops them;
 * an LTIP amount above nought drops a recorded "None".
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

    /**
     * Whether writing figures in {@code newCurrency} would leave a held figure it does not replace labelled in the wrong
     * currency — relabelling it would restate somebody else's number.
     */
    public boolean wouldRelabel(String newCurrency, boolean replacesBase, boolean replacesBonus,
                                boolean replacesAllowances) {
        if (currency == null || currency.equalsIgnoreCase(newCurrency)) {
            return false;
        }
        return longTermIncentive != null || (baseSalary != null && !replacesBase) || (bonus != null && !replacesBonus)
                || (allowances != null && !replacesAllowances);
    }

    /** Nothing about the package is established — no figure, no currency, no notice, no breakdown. */
    public boolean isUnknown() {
        return equals(unknown());
    }
}
