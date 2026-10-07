package app.lightmove.api.mcp.dto;

import app.lightmove.api.mcp.constant.McpCredentialKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** What {@code uncava_whoami} answers: the connection, never anything it can read. */
public record McpWhoAmI(
        McpCredentialKind credentialKind,
        UUID workspaceId,
        @Schema(description = "The OAuth client this connection was granted to; none for an API key", nullable = true)
        String clientId,
        /** What the connection may read, in the scopes' own order. */
        List<String> scopes
) {}
