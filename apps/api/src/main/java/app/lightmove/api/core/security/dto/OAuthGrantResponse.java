package app.lightmove.api.core.security.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An AI app connected to the workspace, as Settings → Connected AI apps lists it. Carries no token. */
public record OAuthGrantResponse(
        UUID id,
        String clientId,
        String clientName,
        /** The host the client's redirect goes to, which is what the consent screen showed. */
        String redirectHost,
        String logoUri,
        List<String> scopes,
        UUID ownerUserId,
        String ownerName,
        Instant connectedAt,
        Instant lastUsedAt,
        /** When the connection lapses unless the app refreshes it first. */
        Instant expiresAt
) {}
