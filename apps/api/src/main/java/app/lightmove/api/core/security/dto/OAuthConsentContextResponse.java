package app.lightmove.api.core.security.dto;

import app.lightmove.api.core.security.oauth.OAuthClientSource;
import java.util.List;

/** What the consent screen needs before anyone presses Allow: who is asking, for what, and into which workspace. */
public record OAuthConsentContextResponse(
        String clientId,
        String clientName,
        /** How the app is known: a metadata document it publishes, a dynamic registration, or one we seeded. */
        OAuthClientSource clientKind,
        /** For a metadata document, the host it was read from — the one fact about the app no one else can claim. */
        String clientHost,
        /** A metadata document on a host we list, or a client we seeded; a dynamic registration never is. */
        boolean verified,
        String clientUri,
        String logoUri,
        String redirectHost,
        /** What the app asked for, in the order the scopes are listed, unknown ones dropped. */
        List<String> requestedScopes,
        List<OAuthConsentWorkspace> workspaces
) {}
