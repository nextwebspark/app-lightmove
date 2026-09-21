package app.lightmove.api.assistant.tool;

import app.lightmove.api.candidate.dto.CandidateResponse;

/**
 * One executive a mandate has mapped, as much of them as a conversation needs.
 *
 * <p>Four fields out of the twenty-odd a profile carries. The emails and phones are deliberately
 * not among them: the contact ledger is what the mandate <i>bought</i>, one lookup at a time, and no
 * question about who has been mapped is answered any better by having it in the context window. The
 * note and the compensation reading are left out for the same reason — they are a researcher's
 * words about a person, not a fact about the map.
 */
public record MandateExecutiveSummary(String fullName, String title, String employer, String status) {

    static MandateExecutiveSummary of(CandidateResponse candidate) {
        return new MandateExecutiveSummary(candidate.fullName(), candidate.title(),
                candidate.companyName(), candidate.status());
    }
}
