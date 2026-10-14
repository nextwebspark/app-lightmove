package app.lightmove.api.task.model;

import app.lightmove.api.task.constant.OutreachFollowUpKind;
import java.time.Instant;
import java.util.UUID;

/**
 * An outreach event that needs a person — a reply, a bounce, a booked call — with the task a rule filed for it,
 * or none where the workspace switched that rule off. Published in the transaction that filed it.
 */
public record OutreachFollowUp(OutreachFollowUpKind kind, UUID workspaceId, UUID projectId, UUID personId,
                               UUID candidateId, UUID recipientUserId, UUID taskId, Instant dueAt) {}
