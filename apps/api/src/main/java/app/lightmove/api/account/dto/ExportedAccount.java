package app.lightmove.api.account.dto;

import java.time.Instant;
import java.util.UUID;

public record ExportedAccount(
        UUID id,
        String email,
        String fullName,
        String title,
        String avatarUrl,
        String timezone,
        String locale,
        String status,
        Instant createdAt,
        Instant emailVerifiedAt,
        Instant lastSignInAt
) {}
