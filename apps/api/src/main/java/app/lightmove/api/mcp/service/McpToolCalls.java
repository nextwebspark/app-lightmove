package app.lightmove.api.mcp.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.mcp.model.McpCallOrigin;
import app.lightmove.api.mcp.model.McpCaller;
import io.modelcontextprotocol.common.McpTransportContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Runs a tool for its caller and writes the call's audit line: tool, credential, project, rows, latency and outcome —
 * never the arguments, which may name people, and never a token. Every tool goes through here.
 */
@Service
@RequiredArgsConstructor
public class McpToolCalls {

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
            throw failed;
        }
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
