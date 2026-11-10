package app.lightmove.api.account.dto;

import java.time.Instant;
import java.util.UUID;

/** One sign-in event of the account's own, from the audit trail. */
public record ExportedSignIn(
        Instant occurredAt,
        String event,
        String outcome,
        String ipAddress,
        String userAgent,
        /** The workspace it opened into, or null for an account-level event. */
        UUID workspaceId
) {}
