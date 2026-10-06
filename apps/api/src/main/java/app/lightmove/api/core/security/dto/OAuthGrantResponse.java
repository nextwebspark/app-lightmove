package app.lightmove.api.core.security.dto;

import app.lightmove.api.core.security.oauth.OAuthClientSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An AI app connected to the workspace, as Settings → Connected AI apps lists it. Carries no token. */
public record OAuthGrantResponse(
        UUID id,
        String clientId,
        String clientName,
        /** How the app is known, as on the consent screen. */
        OAuthClientSource clientKind,
        /** For a metadata document, the host it was read from; what draws Claude's or ChatGPT's own mark. */
        String clientHost,
        /** As on the consent screen: the app is the one it says it is. */
        boolean verified,
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
