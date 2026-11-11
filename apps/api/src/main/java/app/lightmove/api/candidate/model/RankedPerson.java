package app.lightmove.api.candidate.model;

import java.util.UUID;

/**
 * Who an assessed executive is, as far as the Ranking chapter draws them — no contact, package or profile beyond it.
 *
 * @param processingRestricted their processing is restricted (V198): still shown to staff, never sent to a model
 */
public record RankedPerson(
        UUID candidateId,
        UUID personId,
        String fullName,
        String title,
        String companyName,
        String companyLogoUrl,
        String enrichedAt,
        String statusLabel,
        String statusColour,
        String stageLabel,
        boolean outOfProcess,
        boolean processingRestricted
) {

    public static RankedPerson of(Candidate candidate, String companyLogoUrl) {
        Person person = candidate.getPerson();
        CandidateStatusDefinition status = candidate.getStatus();
        return new RankedPerson(candidate.getId(), person.getId(), person.getFullName(), person.getTitle(),
                candidate.getCompanyName(), companyLogoUrl, person.getProfile().enrichedAt(), status.getLabel(),
                status.getColour().value(), status.getStage().getLabel(), status.isOutOfProcess(),
                person.isProcessingRestricted());
    }
}
