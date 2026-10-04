package app.lightmove.api.core.resilience.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ResilienceSettings;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Wraps one HTTP attempt: takes the rate-limit permit and turns transport and body failures, which
 * never reach the status handler, into {@link VendorException}; anything else propagates untouched.
 *
 * <p><b>Call inside the retry, never around it</b> — the permit is per attempt, or retries burst past
 * the cap — and never inside a transaction, where the wait would hold a connection for seconds.
 */
@Component
@Slf4j
public class VendorCallGuard {

    private final VendorRateLimiter rateLimiter;
    private final ResilienceSettings settings;

    public VendorCallGuard(VendorRateLimiter rateLimiter, LightMoveProperties properties) {
        this.rateLimiter = rateLimiter;
        this.settings = properties.resilience();
    }

    public <T> T call(VendorCall call, Supplier<T> attempt) {
        if (!rateLimiter.tryAcquire(call.vendor(), settings.permitMaxWait())) {
            throw new VendorException(call, VendorFailureKind.RATE_LIMITED, null);
        }
        try {
            return attempt.get();
        } catch (VendorResponseFailure classified) {
            throw new VendorException(call, classified.getKind(), classified);
        } catch (RestClientException noAnswer) {
            VendorFailureKind kind = VendorFailureKind.of(noAnswer);
            if (kind == VendorFailureKind.MALFORMED_RESPONSE) {
                // The fall-through for anything unrecognised, and it is not retryable — so an
                // unclassifiable transport problem quietly costs an enrichment. Nameable in a log.
                log.debug("Unclassified failure on {}", call, noAnswer);
            }
            throw new VendorException(call, kind, noAnswer);
        }
    }
}
