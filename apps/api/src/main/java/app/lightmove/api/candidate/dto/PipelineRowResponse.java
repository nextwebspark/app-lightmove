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
        /**
         * Why a client seat cannot see them whatever {@code clientVisible} says (#1488): {@code processing_restricted},
         * {@code share_declined} or {@code share_withdrawn}; null when nothing hides them. Staff-only, like the row.
         */
        String clientHiddenReason,
        /** When research filled the profile in; the photo is asked for only once it has. */
        String enrichedAt,
        /** Why they left, while they are out of process and someone has said; null otherwise. */
        CandidateExitReasonResponse exitReason
) {}
