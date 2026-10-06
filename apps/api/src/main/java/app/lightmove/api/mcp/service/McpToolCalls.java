package app.lightmove.api.mcp.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.mcp.model.McpCallOrigin;
import app.lightmove.api.mcp.model.McpCaller;
import app.lightmove.api.mcp.model.McpToolRefusal;
import io.modelcontextprotocol.common.McpTransportContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Runs a tool for its caller and writes the call's audit line: tool, credential, project, rows, latency and outcome —
 * never the arguments, which may name people, and never a token. Every tool goes through here, and every failure
 * leaves as a {@link McpToolRefusal} carrying a fixed sentence, never an exception's own message.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolCalls {

    static final String COULD_NOT_ANSWER = "Uncava could not answer this call. Try again in a moment.";

    /** A foreign workspace's position and one the caller holds no seat on read alike, as on the public API. */
    private static final Set<ErrorCode> UNREADABLE_POSITION =
            Set.of(ErrorCode.NOT_FOUND, ErrorCode.FORBIDDEN, ErrorCode.NOT_A_MEMBER);

    private final AuditService audit;
    private final Clock clock;

    public <T> T call(McpTransportContext context, String tool, @Nullable UUID projectId,
                      Function<McpCaller, T> body, ToIntFunction<T> rowsOf) {
        McpCaller caller = callerOf(context);
        Instant started = clock.instant();
        try {
            T result = body.apply(caller);
            record(context, caller, tool, projectId, started).detail("rows", rowsOf.applyAsInt(result)).record();
            return result;
        } catch (RuntimeException failed) {
            record(context, caller, tool, projectId, started).failed()
                    .reason(failed.getClass().getSimpleName())
                    .record();
            throw refusalOf(failed, projectId);
        }
    }

    private static McpToolRefusal refusalOf(RuntimeException failed, @Nullable UUID projectId) {
        if (failed instanceof McpToolRefusal refusal) {
            return refusal;
        }
        if (failed instanceof ApiException refused) {
            if (projectId != null && UNREADABLE_POSITION.contains(refused.getCode())) {
                return new McpToolRefusal("No position " + projectId + " is readable through this connection. "
                        + "Find the positions it can read with uncava_search_positions.");
            }
            // User-facing by design, as the REST problem body carries them: never the internal detail.
            if (refused.getFieldErrors() != null && !refused.getFieldErrors().isEmpty()) {
                return new McpToolRefusal(String.join(" ", refused.getFieldErrors().values()));
            }
            return new McpToolRefusal(refused.getClientDetail() != null ? refused.getClientDetail()
                    : refused.getCode().defaultMessage());
        }
        log.error("An MCP tool failed", failed);
        return new McpToolRefusal(COULD_NOT_ANSWER);
    }

    /** The caller the transport put in the context; a tool reached without one is a wiring fault, never a guest. */
    public static McpCaller callerOf(McpTransportContext context) {
        if (context.get(McpCaller.CONTEXT_KEY) instanceof McpCaller caller) {
            return caller;
        }
        throw new IllegalStateException("An MCP tool ran with no caller");
    }

    private AuditService.Builder record(McpTransportContext context, McpCaller caller, String tool,
                                        @Nullable UUID projectId, Instant started) {
        AuditService.Builder event = audit.event(ProjectEventType.MCP_TOOL_CALL)
                .actor(caller.userId())
                .workspace(caller.workspaceId())
                .detail("tool", tool)
                .detail("credentialKind", caller.credentialKind().name())
                .detail("credentialId", caller.credentialId().toString())
                .detailIfPresent("clientId", caller.clientId())
                .detail("latencyMs", Duration.between(started, clock.instant()).toMillis());
        if (context.get(McpCallOrigin.CONTEXT_KEY) instanceof McpCallOrigin origin) {
            event.origin(origin.ipAddress(), origin.userAgent());
        }
        if (projectId != null) {
            event.target(AuditService.PROJECT_TARGET, projectId);
        }
        return event;
    }
}
