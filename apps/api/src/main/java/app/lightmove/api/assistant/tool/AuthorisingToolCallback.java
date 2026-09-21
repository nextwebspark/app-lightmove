package app.lightmove.api.assistant.tool;

import app.lightmove.api.assistant.service.AssistantEventSink;
import app.lightmove.api.core.audit.constant.SecurityEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.logging.service.CorrelationId;
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

    /**
     * What the model is told when the tool itself failed, whatever went wrong inside it.
     *
     * <p>Letting the exception propagate reads as the safer choice and is not.
     * {@code MethodToolCallback} wraps whatever a tool body throws in a
     * {@code ToolExecutionException}, and {@code spring.ai.tools.throw-exception-on-error} defaults
     * to false, so {@code DefaultToolExecutionExceptionProcessor} hands the <b>cause's message</b>
     * back as the tool result and the model reads it. {@code ApiException} licenses its internal
     * detail to quote a column or a rejected value precisely because it never leaves the server,
     * and inside a tool body that stopped being true.
     *
     * <p>Distinct from {@link #REFUSED} because the two are different answers and the model should
     * act on them differently — retry a failure, do not retry a refusal. Neither leaks why: a
     * refusal is uniform across its causes and so is this, and reaching this one only tells a
     * caller the guard let them through, which they already knew.
     */
    static final String FAILED = "That did not work. The tool could not answer.";

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

    /**
     * The turn's, captured on the worker thread that adopted it. A tool runs wherever the advisor's
     * chain does, and the MDC there is empty.
     */
    private final String correlationId;

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
        String result = answerFrom(toolName, toolInput, toolContext);
        sink.toolResult(toolName, result);
        return result;
    }

    /**
     * The tool's own answer, or a fixed sentence when it could not give one.
     *
     * <p>The catch is broad because the leak is: any message reaching the model is one the server
     * wrote for the server. What went wrong stays in the log, where it is a bug report rather than
     * a sentence a conversation can quote back to whoever asked.
     */
    private String answerFrom(String toolName, String toolInput, ToolContext toolContext) {
        try {
            return delegate.call(toolInput, toolContext);
        } catch (RuntimeException failed) {
            log.error("Assistant turn {} failed inside tool {}", caller.turnId(), toolName, failed);
            return FAILED;
        }
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
        CorrelationId.adopt(correlationId);
        try {
            audit.event(SecurityEventType.ASSISTANT_TOOL_DENIED)
                    .actor(calling.userId())
                    .workspace(calling.workspaceId())
                    .target("assistantTurn", calling.turnId())
                    .failed()
                    .reason(denied.getCode().name())
                    .detail("tool", getToolDefinition().name())
                    .record();
        } finally {
            CorrelationId.release();
        }
    }
}
