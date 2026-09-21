package app.lightmove.api.assistant.tool;

import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;

/**
 * How the caller travels from the turn to the tool, and the one place its key is spelled.
 *
 * <p>Spring AI carries an arbitrary map from the request spec's {@code .toolContext(...)} through to
 * every {@code ToolCallback.call}, which is the only channel a decorator has for per-call state. A
 * string key on both sides is exactly the kind of pair that drifts, so neither side writes it.
 */
public final class ToolCallerContext {

    private static final String CALLER = "lightmove.assistant.toolCaller";

    private ToolCallerContext() {
    }

    /** What the turn hands to {@code .toolContext(...)}. */
    public static Map<String, Object> of(AssistantToolCaller caller) {
        return Map.of(CALLER, caller);
    }

    /**
     * The caller a decorated call was made on behalf of, or null where there is none.
     *
     * <p>Null is not an error here — it is the decorator's to refuse, which keeps the decision in one
     * place rather than splitting it between reading and acting on the absence.
     */
    public static AssistantToolCaller callerOf(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        return toolContext.getContext().get(CALLER) instanceof AssistantToolCaller caller ? caller : null;
    }
}
