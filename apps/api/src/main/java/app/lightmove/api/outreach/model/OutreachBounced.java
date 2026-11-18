package app.lightmove.api.outreach.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** A sequence's email bounced, published in the transaction that records it. */
@NamedInterface(name = "api", propagate = false)
public record OutreachBounced(UUID workspaceId, UUID projectId, UUID candidateId, UUID personId, UUID enrollmentId,
                              UUID senderUserId) {}
