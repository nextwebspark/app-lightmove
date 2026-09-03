package app.lightmove.api.core.resilience.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ResilienceSettings;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Wraps one HTTP attempt: takes the rate-limit permit, and makes sure whatever comes back out is a
 * {@link VendorException} rather than a Spring one.
 *
 * <p>This exists alongside the factory's status handler because two failure kinds never reach that
 * handler — there is no status to hand it. {@code RestClient} raises {@code ResourceAccessException}
 * for a transport failure before any response exists, and wraps an unreadable body as a plain
 * {@code RestClientException}; classifying from status alone would leave a dropped connection
 * unclassified and therefore un-retried.
 *
 * <p>Anything that is not a {@code RestClientException} propagates untouched: a
 * {@code NullPointerException} in our own mapping code is not a vendor failure, and dressing it as one
 * would make it retryable and hide it.
 *
 * <p><b>Call this inside the retry, not around it.</b> The permit must be taken per attempt —
 * outside, three attempts spend one permit and burst straight past the cap the permit exists to
 * respect, generating the 429s being retried. And never inside a transaction: a permit wait plus
 * backoff holds a database connection for seconds.
 */
@Component
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
            throw new VendorException(call, VendorFailureKind.of(noAnswer), noAnswer);
        }
    }
}
