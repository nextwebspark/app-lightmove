package app.lightmove.api.core.security.rbac;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Gates a high-impact controller method on one platform action and a two-factor code entered within the step-up
 * window (#1313). Used in place of {@link RequirePlatformPermission}, never beside it: a method takes one
 * {@code @PreAuthorize}.
 */
@Target(ElementType.METHOD)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@platformAuthorizer.canWithRecentTwoFactor(principal, '{value}')")
public @interface RequireRecentTwoFactor {

    PlatformAction value();
}
