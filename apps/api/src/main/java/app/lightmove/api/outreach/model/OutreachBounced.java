package app.lightmove.api.outreach.model;

import java.util.UUID;

/** A sequence's email bounced, published in the transaction that records it. */
public record OutreachBounced(UUID workspaceId, UUID projectId, UUID candidateId, UUID personId, UUID enrollmentId,
                              UUID senderUserId) {}
