package app.lightmove.api.retention.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Someone due for deletion, as an admin reviews them: who, where they were last, when anything last happened to them
 * and what, how many (archived) positions held them, and their owner.
 */
public record RetentionDuePersonResponse(
        UUID personId,
        String fullName,
        String title,
        String employer,
        Instant lastActivityAt,
        /** A timeline kind, {@code RETENTION_KEPT} for an admin's keep, or null where nothing was ever recorded. */
        String lastActivityKind,
        int positions,
        String ownerName
) {}
