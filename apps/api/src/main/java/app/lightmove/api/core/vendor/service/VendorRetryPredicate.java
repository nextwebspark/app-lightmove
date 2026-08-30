package app.lightmove.api.core.vendor.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.VendorSettings;
import app.lightmove.api.core.vendor.model.VendorException;
import java.lang.reflect.Method;
import org.springframework.resilience.retry.MethodRetryPredicate;

/**
 * The one place that answers "try that vendor call again?".
 *
 * <p>Named by every {@code @Retryable} in the codebase, so the decision is written once and every
 * adapter inherits it. Without a predicate the annotation retries <i>any</i> exception, which here
 * would mean paying three times over for a 401 that will never succeed and a 422 that is our own bug.
 *
 * <p>Not a {@code @Component}: Spring instantiates a predicate per annotated method through
 * {@code beanFactory.createBean}, so the constructor is autowired but no bean is registered.
 *
 * <p>The cause chain is walked explicitly. {@code @Retryable}'s {@code includes}/{@code excludes}
 * match nested causes, but a predicate is handed whatever was thrown — so a {@code VendorException}
 * arriving wrapped would silently read as "not retryable" if this only looked at the top.
 */
public class VendorRetryPredicate implements MethodRetryPredicate {

    private final VendorSettings settings;

    public VendorRetryPredicate(LightMoveProperties properties) {
        this.settings = properties.vendor();
    }

    @Override
    public boolean shouldRetry(Method method, Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof VendorException failed) {
                return failed.getFailure().worthRetrying(failed.getCall(), settings.retryAfterCeiling());
            }
        }
        // Not a classified vendor failure, so it is one of ours. Repeating a bug is still a bug.
        return false;
    }
}
