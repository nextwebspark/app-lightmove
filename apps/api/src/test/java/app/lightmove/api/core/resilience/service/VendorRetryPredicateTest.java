package app.lightmove.api.core.resilience.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What every {@code @Retryable} vendor call inherits. Without a predicate the annotation retries any
 * exception, which here means paying three times over for a key that will never work.
 */
class VendorRetryPredicateTest {

    private static final Method ANY_METHOD = VendorRetryPredicateTest.class.getMethods()[0];

    private final VendorRetryPredicate predicate = new VendorRetryPredicate();

    @Test
    @DisplayName("a transient vendor failure is retried and a permanent one is not")
    void theKindDecides() {
        assertThat(predicate.shouldRetry(ANY_METHOD, failure(VendorFailureKind.RATE_LIMITED))).isTrue();
        assertThat(predicate.shouldRetry(ANY_METHOD, failure(VendorFailureKind.UNAVAILABLE))).isTrue();
        assertThat(predicate.shouldRetry(ANY_METHOD, failure(VendorFailureKind.CREDENTIALS))).isFalse();
        assertThat(predicate.shouldRetry(ANY_METHOD, failure(VendorFailureKind.QUOTA_EXHAUSTED))).isFalse();
    }

    @Test
    @DisplayName("a wrapped vendor failure is still found, not read as a bug of ours")
    void theCauseChainIsWalked() {
        Throwable wrapped = new IllegalStateException("mapping blew up",
                failure(VendorFailureKind.UNAVAILABLE));

        assertThat(predicate.shouldRetry(ANY_METHOD, wrapped)).isTrue();
    }

    @Test
    @DisplayName("an exception that is not a vendor failure is one of ours, and repeating a bug is still a bug")
    void oursIsNeverRetried() {
        assertThat(predicate.shouldRetry(ANY_METHOD, new NullPointerException())).isFalse();
    }

    private static VendorException failure(VendorFailureKind kind) {
        return new VendorException(VendorCall.of("brightdata", "profile-search"), kind, null);
    }
}
