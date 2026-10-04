package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.CandidateStatus;
import java.util.List;
import java.util.UUID;

/**
 * An executive as outreach reads them: enough to decide whether they may be approached and to fill a
 * sequence's tokens. Never sent to a model — the opener is drafted from the {@link CandidateDossier}.
 */
public record OutreachRecipient(UUID candidateId, UUID personId, UUID triageCompanyId, String fullName,
                                String title, String companyName, String locationCity, String locationCountry,
                                CandidateStatus status, boolean doNotContact, List<RecipientEmail> emails) {

    public boolean holdsEmail(String address) {
        return address != null && emails.stream()
                .anyMatch(email -> email.address().equalsIgnoreCase(address.trim()));
    }
}
