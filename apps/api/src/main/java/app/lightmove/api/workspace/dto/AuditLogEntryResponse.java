package app.lightmove.api.workspace.dto;

import app.lightmove.api.workspace.constant.AuditActorKind;
import java.time.Instant;
import java.util.UUID;

/** One line of the audit log; {@code group} is null for a type no Category lists. Metadata is never carried. */
public record AuditLogEntryResponse(
        long id,
        Instant occurredAt,
        String type,
        String group,
        String outcome,
        AuditActorKind actorKind,
        UUID actorUserId,
        String actorName,
        String actorAvatarUrl,
        boolean actorRemoved,
        String summary,
        String positionTitle,
        String ipAddress
) {}
