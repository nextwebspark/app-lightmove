package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.UUID;

/** One mandate a person is mapped on, with that mandate's own status and who filed them there. */
public record PersonPositionResponse(
        UUID candidateId,
        UUID projectId,
        String positionTitle,
        /** A {@code CandidateStatus} wire token. */
        String status,
        UUID addedByUserId,
        String addedByName,
        Instant addedAt,
        /** A {@code CandidateSource} wire token: the door this mandate filed them through. */
        String source,
        /** Whether the caller may move this mapping's status — they hold the position's WORK_EXECUTE. */
        boolean workable
) {}
