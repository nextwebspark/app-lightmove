package app.lightmove.api.core.resilience.service;

import app.lightmove.api.core.resilience.model.VendorException;
import java.lang.reflect.Method;
import org.springframework.resilience.retry.MethodRetryPredicate;

/**
 * The one answer to "retry that vendor call?" for every {@code @Retryable}, which otherwise retries
 * anything, 401s included. Not a bean (Spring instantiates it per method); walks the cause chain
 * because a wrapped {@link VendorException} would otherwise read as not retryable.
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
