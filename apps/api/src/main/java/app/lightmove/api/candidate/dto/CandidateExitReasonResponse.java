package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Why a mandate's candidate left the process, as staff read it. Never on {@link CandidateResponse}: a client seat reads
 * that, and a reason is the team's own judgement.
 */
public record CandidateExitReasonResponse(
        UUID candidateId,
        UUID reasonId,
        String label,
        String note,
        boolean retired,
        Instant setAt,
        UUID setByUserId,
        String setByName
) {}
