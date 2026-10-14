package app.lightmove.api.outreach.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A call was booked with an executive on a position — by the consultant from the drawer, or by the executive
 * through the consultant's link ({@code viaLink}) — published in the transaction that records it.
 */
public record OutreachCallBooked(UUID workspaceId, UUID projectId, UUID candidateId, UUID personId,
                                 UUID consultantUserId, Instant startsAt, Instant endsAt, boolean viaLink) {}
