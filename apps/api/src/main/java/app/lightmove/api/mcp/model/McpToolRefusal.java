package app.lightmove.api.mcp.model;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * A tool call answered as an {@code isError} result the model can act on. An {@link McpError} because Spring AI's
 * method callback rethrows only those: anything else it turns into a result quoting the exception's own message, which
 * for an {@code ApiException} is the internal detail. The sentence is fixed text, or an {@code ApiException}'s
 * user-facing sentence or field errors, which are written for the caller by design; never its internal detail.
 */
public class McpToolRefusal extends McpError {

    private final String sentence;

    public McpToolRefusal(String sentence) {
        super(new McpSchema.JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.INTERNAL_ERROR, sentence, null));
        this.sentence = sentence;
    }

    public String sentence() {
        return sentence;
    }
}
