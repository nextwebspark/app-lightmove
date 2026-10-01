package app.lightmove.api.candidate.dto;

import java.util.List;
import java.util.UUID;

/**
 * What staff read beside one row of the position's Candidates page and a client seat never does: the
 * person's tags, the other positions they sit on, who filed them here, do not contact and their latest
 * line. Its own read for the reason {@code report/team} is: {@code CandidateResponse} is the client's too.
 */
public record CandidatePipelineStaffRowResponse(
        UUID candidateId,
        List<UUID> tagIds,
        /** The person's other positions, newest first; this one is left out. */
        List<PersonPositionResponse> alsoIn,
        UUID addedByUserId,
        String addedByName,
        /** Null unless the person is marked do not contact. */
        DoNotContactResponse doNotContact,
        /** Null for someone with no line yet. */
        PersonTimelineEntryResponse lastActivity
) {}
