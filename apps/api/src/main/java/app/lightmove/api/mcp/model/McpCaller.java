package app.lightmove.api.mcp.model;

import app.lightmove.api.core.security.apikey.ApiKeyScope;
import app.lightmove.api.core.security.apikey.PublicReader;
import app.lightmove.api.mcp.constant.McpCredentialKind;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Whoever an MCP call is, whichever credential it came with: the workspace every read is scoped to, and the user whose
 * live permissions bound it — none for a workspace key, which reads its whole workspace as on the public API.
 */
public record McpCaller(
        McpCredentialKind credentialKind,
        @Nullable UUID userId,
        UUID workspaceId,
        Set<ApiKeyScope> scopes,
        /** The OAuth client's id, or nothing for a key. */
        @Nullable String clientId,
        /** The grant behind an OAuth token, or the key itself. */
        UUID credentialId
) {

    /** The key a tool reads the caller from in the MCP transport context. */
    public static final String CONTEXT_KEY = "lightmove.mcp.caller";

    public boolean holds(ApiKeyScope scope) {
        return scopes.contains(scope);
    }

    /** As the public reads see it: a workspace key, the one caller with no user, reads its whole workspace. */
    public PublicReader reader() {
        return new PublicReader(workspaceId, userId, scopes);
    }
}
