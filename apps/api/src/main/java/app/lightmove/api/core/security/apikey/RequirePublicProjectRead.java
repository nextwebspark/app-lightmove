package app.lightmove.api.core.security.apikey;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Gates a public API method on one scope and on reading the position named {@code projectId}. One
 * annotation for both, because Spring Security refuses two {@code @PreAuthorize} on one method.
 */
@Target(ElementType.METHOD)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@publicApiAuthorizer.canReadProject(principal, #projectId, '{value}')")
public @interface RequirePublicProjectRead {

    ApiKeyScope value();
}
