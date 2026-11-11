package app.lightmove.api.report.dto;

import java.util.UUID;

/**
 * One assessed executive of the position.
 *
 * @param enrichedAt           when their research landed; the photo is asked for only then
 * @param processingRestricted their processing is restricted: shown to staff, never sent to a model
 */
public record RankedCandidateDto(
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
        boolean processingRestricted,
        RankedAssessmentDto assessment
) {}
