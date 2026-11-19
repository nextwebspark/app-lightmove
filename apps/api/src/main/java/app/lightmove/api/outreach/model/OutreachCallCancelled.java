package app.lightmove.api.outreach.model;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** A call booked in Uncava was cancelled from the drawer, published in the transaction that records the cancel. */
@NamedInterface(name = "api", propagate = false)
public record OutreachCallCancelled(UUID workspaceId, UUID projectId, UUID personId, UUID cancelledBy,
                                    Instant startsAt, Instant endsAt) {}
