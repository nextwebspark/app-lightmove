package app.lightmove.api.assistant.tool;

import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.WorkspaceAction;

/**
 * What one tool requires before its body may run — a declaration, with no opinion on how it is
 * enforced.
 *
 * <p>Sealed so {@link ToolAuthoriser} switches exhaustively: a third tier would fail to compile
 * there rather than fall through to an else that let the call past.
 */
public sealed interface ToolPermission {

    /** A tool over data that belongs to the workspace rather than to any one mandate. */
    record WorkspaceActionRequired(WorkspaceAction action) implements ToolPermission {

        public WorkspaceActionRequired {
            if (action == null) {
                throw new IllegalArgumentException("a workspace-tier tool must name its action");
            }
        }
    }

    /**
     * A tool over one mandate's own rows.
     *
     * @param projectIdArgument the argument naming the mandate, resolved against the model's own
     *                          arguments at every call
     */
    record ProjectActionRequired(ProjectAction action, String projectIdArgument)
            implements ToolPermission {

        public ProjectActionRequired {
            if (action == null) {
                throw new IllegalArgumentException("a project-tier tool must name its action");
            }
            if (projectIdArgument == null || projectIdArgument.isBlank()) {
                throw new IllegalArgumentException(
                        "a project-tier tool must name the argument holding the project id");
            }
        }
    }
}
