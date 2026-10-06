package app.lightmove.api.mcp.service;

import app.lightmove.api.core.security.apikey.ApiKeyScope;
import app.lightmove.api.mcp.model.McpCaller;
import app.lightmove.api.mcp.model.McpToolRefusal;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stands between the server and every tool: a scope the caller lacks is a result naming it, a {@link McpToolRefusal}
 * becomes the {@code isError} result it describes, and an answer still past the size cap is refused rather than sent.
 */
@RequiredArgsConstructor
public class McpToolGuard {

    static final String TOO_LARGE = "This answer is too large to return in one call. Narrow it: filter by stage or "
            + "company, ask for fewer rows with limit, or use response_format=concise.";

    private final JsonMapper json;
    private final int maxResultChars;

    public SyncToolSpecification guard(SyncToolSpecification tool, Set<ApiKeyScope> needed) {
        return SyncToolSpecification.builder()
                .tool(tool.tool())
                .callHandler((context, request) -> {
                    McpCaller caller = McpToolCalls.callerOf(context);
                    List<String> missing = needed.stream().filter(scope -> !caller.holds(scope))
                            .map(ApiKeyScope::value).sorted().toList();
                    if (!missing.isEmpty()) {
                        return refused("This connection lacks " + missing.stream().collect(Collectors.joining(" and "))
                                + ". Its owner can grant it in Uncava (Settings → API keys, or reconnect the app).");
                    }
                    CallToolResult result;
                    try {
                        result = tool.callHandler().apply(context, request);
                    } catch (McpToolRefusal refusal) {
                        return refused(refusal.sentence());
                    }
                    if (result.structuredContent() != null
                            && json.writeValueAsString(result.structuredContent()).length() > maxResultChars) {
                        return refused(TOO_LARGE);
                    }
                    return result;
                })
                .build();
    }

    private static CallToolResult refused(String sentence) {
        return CallToolResult.builder().isError(true).addTextContent(sentence).build();
    }
}
