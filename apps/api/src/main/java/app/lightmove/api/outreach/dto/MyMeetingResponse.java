package app.lightmove.api.outreach.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One meeting on the caller's own calendar today, with the executive it is with. The position fields are null when
 * that executive is on none of the caller's live positions; {@code joinUrl} only until the call has ended.
 */
public record MyMeetingResponse(UUID id, String title, Instant startsAt, Instant endsAt, String joinUrl,
                                String videoProvider, UUID personId, String personName, UUID projectId,
                                UUID candidateId, String positionTitle, String clientName) {}
