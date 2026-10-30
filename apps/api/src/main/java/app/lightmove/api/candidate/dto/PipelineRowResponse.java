package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One executive as the Pipeline page draws them: who, where they stand, since when, and what happens next.
 * Staff-only — the next action and the visibility switch are the team's own working facts.
 */
public record PipelineRowResponse(
        UUID candidateId,
        UUID personId,
        String fullName,
        String title,
        String companyName,
        UUID triageCompanyId,
        String status,
        String statusLabel,
        String statusColour,
        /** Whoever holds an out-of-process status is drawn in Inactive, whatever stage it sits under. */
        boolean outOfProcess,
        CandidatePipelineStageDto pipelineStage,
        Instant addedAt,
        Instant statusChangedAt,
        CandidateNextActionDto nextAction,
        boolean clientVisible,
        /** When research filled the profile in; the photo is asked for only once it has. */
        String enrichedAt
) {}
