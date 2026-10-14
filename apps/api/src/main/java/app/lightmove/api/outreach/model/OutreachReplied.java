package app.lightmove.api.outreach.model;

import java.util.UUID;

/** A person answered a sequence, published in the transaction that records it; only that they did, never what. */
public record OutreachReplied(UUID workspaceId, UUID projectId, UUID candidateId, UUID personId, UUID enrollmentId,
                              UUID senderUserId) {}
