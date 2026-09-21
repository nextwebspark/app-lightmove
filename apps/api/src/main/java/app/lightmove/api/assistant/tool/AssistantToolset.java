package app.lightmove.api.assistant.tool;

import app.lightmove.api.assistant.service.AssistantEventSink;
import app.lightmove.api.core.audit.service.AuditService;
import java.util.Arrays;
import java.util.List;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

/**
 * The assistant's tools, handed out already guarded and bound to one turn.
 *
 * <p><b>Nothing here is registered as a {@code ToolCallback} or {@code ToolCallbackProvider} bean,
 * and that is the design.</b> {@code ToolCallingAutoConfiguration} folds every such bean into one
 * resolver keyed on tool <i>name</i>, so a raw callback left in the context would be reachable by
 * any call naming that string and would run with no guard at all. Building them here, per turn,
 * means an undecorated callback never exists outside this method.
 *
 * <p>The permissions and the reflection behind each callback are both resolved once at construction:
 * a tool whose guard was forgotten fails the application at startup rather than the first
 * conversation that reaches for it, and a turn pays for a wrapper rather than for a class scan.
 */
@Component
public class AssistantToolset {

    private final ToolCallback[] undecorated;
    private final ToolPermissions permissions;
    private final ToolAuthoriser authoriser;
    private final AuditService audit;

    public AssistantToolset(List<AssistantToolSubject> subjects, ToolAuthoriser authoriser,
                            AuditService audit) {
        List<Object> toolObjects = List.copyOf(subjects);
        this.undecorated = MethodToolCallbackProvider.builder()
                .toolObjects(toolObjects.toArray())
                .build()
                .getToolCallbacks();
        this.permissions = new ToolPermissions(toolObjects);
        this.authoriser = authoriser;
        this.audit = audit;
    }

    /** Every tool, guarded, traced, and answering only for this caller's turn. */
    public ToolCallback[] forTurn(AssistantToolCaller caller, AssistantEventSink sink) {
        return Arrays.stream(undecorated)
                .map(tool -> (ToolCallback) new AuthorisingToolCallback(
                        tool, permissions, authoriser, caller, audit, sink))
                .toArray(ToolCallback[]::new);
    }

    /** What this holds, so a test can assert the surface rather than infer it. */
    public ToolPermissions permissions() {
        return permissions;
    }
}
