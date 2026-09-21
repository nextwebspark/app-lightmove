package app.lightmove.api.assistant.tool;

import app.lightmove.api.assistant.service.AssistantEventSink;
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
    private static final String SINK = "lightmove.assistant.eventSink";

    /** Everything a sink does is optional, so a sink that does none of it is a legitimate one. */
    private static final AssistantEventSink UNWATCHED = text -> {
    };

    private ToolCallerContext() {
    }

    /**
     * What the turn hands to {@code .toolContext(...)}.
     *
     * <p>The sink travels beside the caller because a tool that emits an event of its own must write
     * through <i>this turn's</i> serialised writer — see {@link AssistantEventSink#proposal}. Both
     * are per-turn state the model must not be able to supply, which is what this channel is for.
     */
    public static Map<String, Object> of(AssistantToolCaller caller, AssistantEventSink sink) {
        return Map.of(CALLER, caller, SINK, sink);
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

    /**
     * This turn's event sink, or a sink that drops what it is given where there is none.
     *
     * <p>Never null, unlike {@link #callerOf}: a missing caller is a refusal the decorator owns,
     * while a missing sink only means nothing is watching — a unit test driving a tool directly, for
     * instance. A tool must not have to decide what an absent sink means.
     */
    public static AssistantEventSink sinkOf(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return UNWATCHED;
        }
        return toolContext.getContext().get(SINK) instanceof AssistantEventSink sink ? sink : UNWATCHED;
    }
}
