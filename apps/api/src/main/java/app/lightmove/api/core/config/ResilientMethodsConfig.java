package app.lightmove.api.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Switches on {@code @Retryable}.
 *
 * <p>A one-line class that is easy to delete and expensive to lose: without it Spring creates no
 * retry proxies at all, every {@code @Retryable} in the codebase silently becomes a plain method
 * call, and nothing fails — the calls simply stop being retried. That is the same shape of bug as a
 * security annotation whose advice was never registered, so {@code CoresignalRetryTest} boots the
 * context and asserts a retry actually happens rather than trusting the annotation is live.
 */
@Configuration(proxyBeanMethods = false)
@EnableResilientMethods
public class ResilientMethodsConfig {
}
