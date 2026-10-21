package app.lightmove.api.outreach.model;

import java.util.UUID;

/**
 * A consultant's own email to an executive went, published in the transaction that records it: whatever reply was
 * waiting on them has been answered.
 */
public record OutreachEmailSentByHand(UUID workspaceId, UUID projectId, UUID personId, UUID candidateId,
                                      UUID senderUserId, UUID messageId) {}
