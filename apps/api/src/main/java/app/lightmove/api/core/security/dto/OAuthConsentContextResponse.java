package app.lightmove.api.core.security.dto;

import java.util.List;

/** What the consent screen needs before anyone presses Allow: who is asking, for what, and into which workspace. */
public record OAuthConsentContextResponse(
        String clientId,
        String clientName,
        String clientUri,
        String logoUri,
        String redirectHost,
        /** What the app asked for, in the order the scopes are listed, unknown ones dropped. */
        List<String> requestedScopes,
        List<OAuthConsentWorkspace> workspaces
) {}
