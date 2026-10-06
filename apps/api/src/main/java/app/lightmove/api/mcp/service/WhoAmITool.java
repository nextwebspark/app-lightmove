package app.lightmove.api.mcp.service;

import app.lightmove.api.core.security.apikey.ApiKeyScope;
import app.lightmove.api.mcp.dto.McpWhoAmI;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/** The tool a client calls first, as the public API's {@code /me}: which workspace it reaches, and what it may read. */
@Component
@RequiredArgsConstructor
public class WhoAmITool {

    static final String NAME = "uncava_whoami";

    private final McpToolCalls calls;

    @McpTool(name = NAME, generateOutputSchema = true, title = "Who am I",
            description = "Describe this connection: the Uncava workspace it reads and the scopes it was granted."
                    + " Needs no scope; call it to check a connection works before reading anything.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public McpWhoAmI whoami(McpTransportContext context) {
        return calls.call(context, NAME, null, caller -> new McpWhoAmI(caller.credentialKind(), caller.workspaceId(),
                caller.clientId(), ApiKeyScope.dataScopes().stream().filter(caller::holds).map(ApiKeyScope::value)
                        .toList()), whoAmI -> 1);
    }
}
