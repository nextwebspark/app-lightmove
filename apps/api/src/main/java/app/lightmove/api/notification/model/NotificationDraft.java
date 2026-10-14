package app.lightmove.api.notification.model;

import app.lightmove.api.notification.constant.NotificationKind;
import java.time.Instant;
import java.util.UUID;

/**
 * One event for one recipient, before it is written or folded into a live row of the same {@code groupKey}.
 * {@code name} is the executive a grouped row lists; {@code count} how many events it stands for.
 */
public record NotificationDraft(
        UUID workspaceId,
        UUID recipientUserId,
        NotificationKind kind,
        UUID actorUserId,
        UUID projectId,
        UUID personId,
        UUID candidateId,
        UUID taskId,
        UUID noteId,
        String groupKey,
        String name,
        Instant dueAt,
        int count,
        Instant emailDueAt
) {

    public NotificationDraft withEmailDueAt(Instant at) {
        return new NotificationDraft(workspaceId, recipientUserId, kind, actorUserId, projectId, personId, candidateId,
                taskId, noteId, groupKey, name, dueAt, count, at);
    }
}
