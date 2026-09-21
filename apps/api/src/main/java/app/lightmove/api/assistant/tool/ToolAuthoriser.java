package app.lightmove.api.assistant.tool;

import app.lightmove.api.assistant.tool.ToolPermission.ProjectActionRequired;
import app.lightmove.api.assistant.tool.ToolPermission.WorkspaceActionRequired;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Enforces a {@link ToolPermission} against the arguments of the call it guards.
 *
 * <p>Calls {@code ProjectAccess} and {@code WorkspaceAccess} imperatively rather than through the
 * {@code @projectAuthorizer} SpEL beans. Method security is proxy-based and evaluates the thread's
 * {@code SecurityContext}, which on an assistant worker is empty — the authorizer beans are
 * documented as belonging on controllers for exactly this reason.
 */
@Service
@RequiredArgsConstructor
public class ToolAuthoriser {

    private final WorkspaceAccess workspaceAccess;
    private final ProjectAccess projectAccess;
    private final ObjectMapper json;

    /** Throws an {@link ApiException} the caller is expected to translate; returns nothing on a pass. */
    public void authorise(AssistantToolCaller caller, ToolPermission permission, String toolArguments) {
        switch (permission) {
            case WorkspaceActionRequired(var action) ->
                    workspaceAccess.requireAction(caller.userId(), caller.workspaceId(), action);
            case ProjectActionRequired(var action, var argument) ->
                    projectAccess.requireAction(caller.userId(), caller.workspaceId(),
                            projectIdFrom(toolArguments, argument), action);
        }
    }

    /**
     * The mandate the model named, refused rather than defaulted when it named none.
     *
     * <p>A null project id would reach {@code ProjectAccess} as a lookup that misses, which refuses
     * too — but by accident and with a different code. Deciding it here keeps a malformed call and an
     * unauthorised one on the same path.
     */
    private UUID projectIdFrom(String toolArguments, String argument) {
        try {
            JsonNode arguments = json.readTree(toolArguments == null ? "{}" : toolArguments);
            JsonNode named = arguments.get(argument);
            if (named == null || !named.isString()) {
                throw new ApiException(ErrorCode.FORBIDDEN,
                        "Tool call supplied no " + argument + " to authorise against");
            }
            return UUID.fromString(named.stringValue());
        } catch (JacksonException | IllegalArgumentException malformed) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Tool call supplied an unreadable " + argument
                    + ": " + malformed.getMessage());
        }
    }
}
