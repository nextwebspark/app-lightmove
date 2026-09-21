package app.lightmove.api.assistant.tool;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.util.ReflectionUtils;

/**
 * Every tool's declared permission, read once from the annotations and refused if any is missing.
 *
 * <p><b>An undeclared tool fails the application at startup rather than at the call.</b> That is the
 * whole point of reading this here: a tool whose guard was forgotten cannot reach production, so the
 * mechanism is enforcement rather than a convention forty methods have to remember. It is the same
 * instinct as {@code PromptGuardSpec} refusing an unrecognisable blocked answer where it is written.
 *
 * <p>Failing closed at lookup too — {@link #requiredBy} throws on a name it does not hold — because
 * the alternative is a tool that arrives from somewhere this class never inspected running
 * unguarded.
 */
public final class ToolPermissions {

    private final Map<String, ToolPermission> byToolName;

    public ToolPermissions(List<Object> toolObjects) {
        Map<String, ToolPermission> resolved = new LinkedHashMap<>();
        List<String> faults = new ArrayList<>();
        for (Object toolObject : toolObjects) {
            readInto(toolObject, resolved, faults);
        }
        if (!faults.isEmpty()) {
            throw new IllegalStateException(
                    "Assistant tools are missing a permission declaration: " + String.join("; ", faults));
        }
        this.byToolName = Map.copyOf(resolved);
    }

    /** The permission guarding a tool, never null: an unknown name is a refusal, not a pass. */
    public ToolPermission requiredBy(String toolName) {
        ToolPermission permission = byToolName.get(toolName);
        if (permission == null) {
            throw new IllegalStateException("Tool " + toolName + " declares no permission");
        }
        return permission;
    }

    /** The tool names this holds, so a test can assert the set rather than guess at it. */
    public Set<String> toolNames() {
        return byToolName.keySet();
    }

    private static void readInto(Object toolObject, Map<String, ToolPermission> resolved,
                                 List<String> faults) {
        ReflectionUtils.doWithMethods(toolObject.getClass(), method -> {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool == null) {
                return;
            }
            String toolName = nameOf(tool, method);
            String where = toolObject.getClass().getSimpleName() + "#" + method.getName();
            try {
                ToolPermission permission = permissionOf(method);
                if (resolved.put(toolName, permission) != null) {
                    faults.add(where + " reuses the tool name '" + toolName + "'");
                }
            } catch (IllegalStateException malformed) {
                faults.add(where + " " + malformed.getMessage());
            }
        });
    }

    /** Spring AI's own rule: the annotation's name where it has one, else the method's. */
    private static String nameOf(Tool tool, Method method) {
        return tool.name() == null || tool.name().isBlank() ? method.getName() : tool.name();
    }

    private static ToolPermission permissionOf(Method method) {
        RequiresWorkspaceAction workspace = method.getAnnotation(RequiresWorkspaceAction.class);
        RequiresProjectAction project = method.getAnnotation(RequiresProjectAction.class);
        if (workspace != null && project != null) {
            throw new IllegalStateException(
                    "declares both a workspace and a project action; a tool is guarded at one tier");
        }
        if (workspace != null) {
            return new ToolPermission.WorkspaceActionRequired(workspace.value());
        }
        if (project != null) {
            requireParameter(method, project.projectId());
            return new ToolPermission.ProjectActionRequired(project.value(), project.projectId());
        }
        throw new IllegalStateException("declares no @RequiresWorkspaceAction or @RequiresProjectAction");
    }

    /**
     * A named argument that does not exist would authorise against a null project id on every call,
     * which reads as a guarded tool and behaves as an unguarded one. Caught here, where the name is
     * written.
     *
     * <p>Requires {@code -parameters}, which the Spring Boot Maven plugin sets by default and which
     * {@code @ToolParam} already depends on for the same reason.
     */
    private static void requireParameter(Method method, String parameterName) {
        boolean declared = Arrays.stream(method.getParameters())
                .map(Parameter::getName)
                .anyMatch(parameterName::equals);
        if (!declared) {
            throw new IllegalStateException("names the project id argument '" + parameterName
                    + "', which it does not declare");
        }
    }
}
