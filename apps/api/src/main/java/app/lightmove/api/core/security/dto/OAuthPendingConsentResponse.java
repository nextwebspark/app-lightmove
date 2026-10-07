package app.lightmove.api.core.security.dto;

import java.util.List;
import java.util.UUID;

/** A request waiting for its consent: the {@code state} the consent is posted with, and what it may grant. */
public record OAuthPendingConsentResponse(
        String clientId,
        String state,
        List<String> requestedScopes,
        UUID workspaceId
) {}
