package app.lightmove.api.candidate.model;

import java.util.UUID;

/**
 * What the record holds now for every field a recorded call may change, so a suggestion is shown against it and one
 * equal to it is never offered. {@code relocationTagId} is the workspace's live "Open to relocate" tag, or null;
 * {@code expectedFiguresHeld} says an expected package is on file in the record's currency.
 */
public record CallHeldFields(String noticePeriod, String currency, Long baseSalary, Long bonus, Long allowances,
                             Long longTermIncentive, String city, String country, String motivation,
                             UUID relocationTagId, boolean openToRelocate, boolean expectedFiguresHeld) {

    public CandidateCompensation compensation() {
        return new CandidateCompensation(currency, baseSalary, bonus, allowances, longTermIncentive, noticePeriod, null);
    }

    /** Whether a call's package in {@code newCurrency} would leave a held figure, expected ones included, mislabelled. */
    public boolean wouldRelabel(String newCurrency, boolean replacesBase, boolean replacesBonus,
                                boolean replacesAllowances) {
        return compensation().wouldRelabel(newCurrency, replacesBase, replacesBonus, replacesAllowances)
                || (expectedFiguresHeld && currency != null && !currency.equalsIgnoreCase(newCurrency));
    }
}
