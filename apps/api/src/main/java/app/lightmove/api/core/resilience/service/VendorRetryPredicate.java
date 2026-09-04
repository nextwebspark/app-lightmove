package app.lightmove.api.core.resilience.service;

import app.lightmove.api.core.resilience.model.VendorException;
import java.lang.reflect.Method;
import org.springframework.resilience.retry.MethodRetryPredicate;

/**
 * The one place that answers "try that vendor call again?".
 *
 * <p>Named by every {@code @Retryable} in the codebase, so the decision is written once. Without a
 * predicate the annotation retries <i>any</i> exception, which here means paying three times over for
 * a 401 that will never succeed.
 *
 * <p>Not a {@code @Component}: Spring instantiates a predicate per annotated method, so the
 * constructor is resolved but no bean is registered. The cause chain is walked explicitly, because a
 * predicate is handed whatever was thrown — a wrapped {@link VendorException} would otherwise read as
 * "not retryable".
 */
public class VendorRetryPredicate implements MethodRetryPredicate {

    @Override
    public boolean shouldRetry(Method method, Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof VendorException failed) {
                return failed.getKind().retryable();
            }
        }
        return false;
    }
}
