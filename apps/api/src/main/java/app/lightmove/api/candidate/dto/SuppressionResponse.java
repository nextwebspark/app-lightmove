package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry of the suppression list. It never carries the profile or address it suppresses — the list holds
 * only their hash — so an admin tells entries apart by kind, reason, note and who added them when.
 */
public record SuppressionResponse(
        UUID id,
        /** A {@code SuppressionKind} wire token: {@code linkedin} or {@code email}. */
        String kind,
        /** A {@code SuppressionReason} wire token. */
        String reason,
        String note,
        AddedByResponse addedBy,
        Instant createdAt
) {}
