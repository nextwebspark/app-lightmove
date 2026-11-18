package app.lightmove.api.outreach.model;

import java.time.Instant;
import java.util.UUID;

/** A call booked in Uncava was moved from the drawer, published in the transaction that records the move. */
public record OutreachCallMoved(UUID workspaceId, UUID projectId, UUID personId, UUID movedBy,
                                Instant previousStartsAt, Instant previousEndsAt, Instant startsAt, Instant endsAt) {}
