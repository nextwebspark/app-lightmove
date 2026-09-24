package app.lightmove.api.project.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One line of a mandate's recent activity: an audit event, its actor resolved to a name, and only
 * the details the side panel phrases — never the raw metadata.
 */
public record ProjectActivityEntryResponse(
        long id,
        String type,
        Instant occurredAt,
        UUID actorUserId,
        String actorName,
        String actorAvatarUrl,
        Map<String, String> details
) {}
