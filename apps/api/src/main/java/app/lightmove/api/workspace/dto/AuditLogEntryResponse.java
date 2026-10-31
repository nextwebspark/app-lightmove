package app.lightmove.api.workspace.dto;

import app.lightmove.api.workspace.constant.AuditActorKind;
import java.time.Instant;
import java.util.UUID;

/**
 * One line of the audit log. {@code summary} is phrased from an allowlist of each type's fields; the row's metadata
 * never leaves the server. {@code group} is null for a type no Category lists.
 */
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
