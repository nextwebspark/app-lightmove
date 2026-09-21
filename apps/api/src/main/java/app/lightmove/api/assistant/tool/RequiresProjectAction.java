package app.lightmove.api.assistant.tool;

import app.lightmove.api.core.security.rbac.ProjectAction;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the project action a tool needs, and which of its arguments names the project.
 *
 * <p>The argument is what makes this safe. The model emits the project id itself — it may have read
 * one earlier in the thread, or inferred one — so the guard runs against the value the model
 * supplied rather than against the thread's own mandate or the screen the panel was opened from.
 * Neither of those proves anything about the call in hand.
 *
 * <p>Exactly one of this and {@link RequiresWorkspaceAction} must be present on every {@code @Tool}
 * method; {@link ToolPermissions} refuses to start the application otherwise.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresProjectAction {

    ProjectAction value();

    /** The parameter holding the project id. Checked against the method's real parameters at wiring. */
    String projectId() default "projectId";
}
