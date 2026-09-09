package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

/**
 * What an executive is paid today, as a researcher records it: the four elements a GCC package is
 * quoted in, plus how long it takes to move them.
 *
 * <p>One currency for the whole package and no conversion — a rate applied at write time is wrong by
 * the time anyone reads the row. Every element is nullable and none is interchangeable with zero:
 * "no bonus" is a fact about the package, "not established yet" a fact about the research.
 */
public record CandidateCompensation(String currency, Long baseSalary, Long bonus, Long allowances,
                                    Long longTermIncentive, String noticePeriod) {

    public CandidateCompensation {
        String supplied = blankToNull(currency);
        currency = supplied == null ? null : supplied.toUpperCase();
        noticePeriod = blankToNull(noticePeriod);
    }

    /** What a candidate carries before anyone has established a package. */
    public static CandidateCompensation unknown() {
        return new CandidateCompensation(null, null, null, null, null, null);
    }
}
