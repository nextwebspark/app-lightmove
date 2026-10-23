package app.lightmove.api.candidate.model;

import java.util.UUID;

/**
 * What the record holds now for every field a recorded call may change, so a suggestion is shown against it and one
 * equal to it is never offered. {@code relocationTagId} is the workspace's live "Open to relocate" tag, or null.
 */
public record CallHeldFields(String noticePeriod, String currency, Long baseSalary, Long bonus, Long allowances,
                             Long longTermIncentive, String city, String country, String motivation,
                             UUID relocationTagId, boolean openToRelocate) {

    public CandidateCompensation compensation() {
        return new CandidateCompensation(currency, baseSalary, bonus, allowances, longTermIncentive, noticePeriod, null);
    }
}
