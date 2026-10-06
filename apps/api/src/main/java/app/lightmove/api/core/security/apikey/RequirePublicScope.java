package app.lightmove.api.core.security.apikey;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/** Gates a public API method on one scope of the calling key. */
@Target(ElementType.METHOD)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@publicApiAuthorizer.holds(principal, '{value}')")
public @interface RequirePublicScope {

    ApiKeyScope value();
}
