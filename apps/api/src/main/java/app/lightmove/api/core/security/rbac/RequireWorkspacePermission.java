package app.lightmove.api.core.security.rbac;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/** Gates a controller method on one workspace action. */
@Target(ElementType.METHOD)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@workspaceAuthorizer.can(principal, '{value}')")
public @interface RequireWorkspacePermission {

    WorkspaceAction value();
}
