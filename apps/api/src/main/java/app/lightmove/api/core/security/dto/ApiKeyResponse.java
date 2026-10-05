package app.lightmove.api.core.security.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A key as Settings lists it. Never its secret: only {@code tokenHint}, which cannot be used. */
public record ApiKeyResponse(
        UUID id,
        String name,
        /** {@code PERSONAL} or {@code SERVICE}. */
        String kind,
        /** {@code ACTIVE}, {@code EXPIRED} or {@code REVOKED}, as of this read. */
        String status,
        String tokenHint,
        List<String> scopes,
        /** Null on a workspace key. */
        UUID ownerUserId,
        String ownerName,
        String createdByName,
        Instant createdAt,
        Instant expiresAt,
        Instant lastUsedAt,
        String lastUsedIp,
        Instant revokedAt,
        String revokedByName,
        String revokedReason
) {}
