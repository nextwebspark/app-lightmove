package app.lightmove.api.core.security.rbac;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/** Gates a controller method on one project action. The method must name its project id {@code projectId}. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@projectAuthorizer.can(principal, #projectId, '{value}')")
public @interface RequireProjectPermission {

    ProjectAction value();
}
