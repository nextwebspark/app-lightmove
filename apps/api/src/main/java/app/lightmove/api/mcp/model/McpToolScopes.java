package app.lightmove.api.mcp.model;

import app.lightmove.api.core.security.apikey.ApiKeyScope;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The scopes an {@code @McpTool} needs, every one of them. Read before the transport runs, so an OAuth token lacking one
 * is answered with the HTTP step-up rather than a tool result.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpToolScopes {

    ApiKeyScope[] value();
}
