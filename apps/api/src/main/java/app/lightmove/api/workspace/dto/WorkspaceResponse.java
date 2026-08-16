package app.lightmove.api.workspace.dto;

import java.time.Instant;
import java.util.UUID;

/** Settings → General. */
public record WorkspaceResponse(
        UUID id,
        String name,
        String slug,
        String logoMark,
        String emailDomain,
        String defaultRegion,
        String defaultCurrency,
        String plan,
        long memberCount,
        Instant createdAt
) {}
