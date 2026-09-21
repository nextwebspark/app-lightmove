package app.lightmove.api.assistant.tool;

import app.lightmove.api.assistant.service.AssistantEventSink;
import app.lightmove.api.core.audit.constant.SecurityEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.model.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * One tool, authorised against the arguments of the call rather than against the conversation.
 *
 * <p>The model emits those arguments. It may name a mandate it read about earlier in the thread, or
 * one it inferred, and the page the panel was opened from proves nothing about the call in hand — so
 * the guard runs per call, and {@link ToolAuthoriser} re-reads the database every time.
 */
@RequiredArgsConstructor
@Slf4j
public class AuthorisingToolCallback implements ToolCallback {

    /**
     * What the model is told about every refusal, whatever the cause.
     *
     * <p>One sentence for all of them on purpose. {@code ProjectAccess} answers 404 for a mandate
     * that does not exist and 403 for a real one the caller has no seat on, which is defensible over
     * HTTP — projects are browsable to staff — and is an enumeration oracle here, where the caller
     * may be a client representative and the questioner can ask again with a different id and read
     * the answers back in prose.
     */
    static final String REFUSED = "Refused: you do not have access to that.";

    private final ToolCallback delegate;
    private final ToolPermissions permissions;
    private final ToolAuthoriser authoriser;
    private final AssistantToolCaller caller;
    private final AuditService audit;

    /**
     * Where the trace goes. The decorator is the only thing that sees both what the model asked for
     * and what came back: the tool loop runs inside Spring AI's advisor, so a tool call never
     * appears in the response stream the runner consumes and a result never appears at all.
     */
    private final AssistantEventSink sink;

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    /**
     * Refused outright, and never delegated.
     *
     * <p>{@code ToolCallback} declares this overload abstract and the one taking a {@link ToolContext}
     * as a default, so a caller reaching for this one supplies no caller and no authorisation is
     * possible. Spring AI's own manager uses the two-argument form; anything arriving here is a
     * caller this class has not been told about, and guessing is the one thing a guard may not do.
     */
    @Override
    public String call(String toolInput) {
        throw new IllegalStateException(
                "Tool " + getToolDefinition().name() + " was called with no tool context");
    }

    /**
     * Guarded twice over, and the second check is not redundant.
     *
     * <p>A callback is built for one turn and carries that turn's caller, so the field alone would
     * answer. But every {@code ToolCallback} bean is resolvable <i>by tool name</i> through Spring
     * AI's shared resolver, so an instance that ever escaped into it would authorise whatever turn
     * found it there against the turn it was born for. Requiring the context to agree makes that
     * impossible rather than merely unlikely; nothing here is registered as a bean either.
     */
    @Override
    public String call(String toolInput, ToolContext toolContext) {
        AssistantToolCaller calling = ToolCallerContext.callerOf(toolContext);
        if (calling == null || !calling.equals(caller)) {
            throw new IllegalStateException(
                    "Tool " + getToolDefinition().name() + " was called for a different turn");
        }
        String toolName = getToolDefinition().name();
        sink.toolCalled(toolName, toolInput);
        try {
            authoriser.authorise(calling, permissions.requiredBy(toolName), toolInput);
        } catch (ApiException denied) {
            recordDenial(calling, denied);
            sink.toolResult(toolName, REFUSED);
            return REFUSED;
        }
        String result = delegate.call(toolInput, toolContext);
        sink.toolResult(toolName, result);
        return result;
    }

    /**
     * A refusal is a conversational outcome and a ledger row, never an exception.
     *
     * <p>Throwing would send the reason to the model instead: Spring AI hands a failed tool call to
     * {@code ToolExecutionExceptionProcessor}, whose answer becomes the tool result, and an
     * {@code ApiException}'s internal detail is allowed to quote the request. That is the sentence
     * this class exists to keep out of the conversation, so it is logged and audited here and the
     * model is told {@link #REFUSED}.
     */
    private void recordDenial(AssistantToolCaller calling, ApiException denied) {
        log.warn("Assistant turn {} was refused tool {}: {}", calling.turnId(),
                getToolDefinition().name(), denied.getCode());
        audit.event(SecurityEventType.ASSISTANT_TOOL_DENIED)
                .actor(calling.userId())
                .workspace(calling.workspaceId())
                .target("assistantTurn", calling.turnId())
                .failed()
                .reason(denied.getCode().name())
                .detail("tool", getToolDefinition().name())
                .record();
    }
}
