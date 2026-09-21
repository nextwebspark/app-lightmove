package app.lightmove.api.assistant.tool;

import app.lightmove.api.core.security.rbac.WorkspaceAction;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the workspace action a tool needs, for a tool that reads nothing mandate-scoped.
 *
 * <p>Use this for the market side — the Apollo universe belongs to no project, so there is no
 * project id to authorise against and {@code PROJECT_BROWSE} is the whole gate. A pure client holds
 * no workspace action at all, which is what keeps them out of it.
 *
 * <p>Exactly one of this and {@link RequiresProjectAction} must be present on every {@code @Tool}
 * method; {@link ToolPermissions} refuses to start the application otherwise.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresWorkspaceAction {

    WorkspaceAction value();
}
