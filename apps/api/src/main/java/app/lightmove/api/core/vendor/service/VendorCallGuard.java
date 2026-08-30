package app.lightmove.api.core.vendor.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.VendorSettings;
import app.lightmove.api.core.vendor.constant.VendorFailureKind;
import app.lightmove.api.core.vendor.model.VendorCall;
import app.lightmove.api.core.vendor.model.VendorException;
import app.lightmove.api.core.vendor.model.VendorFailure;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Wraps one HTTP attempt: takes the rate-limit permit, and makes sure whatever comes back out is a
 * classified {@link VendorException} rather than a Spring one.
 *
 * <p><b>Why this exists alongside the status handler.</b> The handler classifies what a vendor
 * <i>answered</i>. Two of the eight failure kinds never reach it, because they happen when there is
 * no answer to have a status: {@code RestClient} raises {@code ResourceAccessException} for a
 * transport failure before any response exists, and wraps an unreadable body as a plain
 * {@code RestClientException}. Classifying only from status codes would let both through
 * unclassified, and therefore un-retried — a dropped connection would look like a bug rather than the
 * most ordinary reason to try again.
 *
 * <p>Anything that is <i>not</i> a {@code RestClientException} is deliberately left to propagate. A
 * {@code NullPointerException} in our own mapping code is not a vendor failure, and dressing it as
 * one would make it retryable and hide it.
 *
 * <p><b>Call this inside the retry, not around it.</b> The permit must be taken per attempt: outside
 * the retry loop, three attempts spend one permit and burst straight past the cap the permit exists
 * to respect — generating the 429s being retried.
 *
 * <p><b>Never call a vendor inside a transaction.</b> A permit wait plus backoff can hold a thread
 * for seconds, and a thread inside {@code @Transactional} is holding a database connection the whole
 * time. The pool is small and shared.
 */
@Component
public class VendorCallGuard {

    private final VendorRateLimiter rateLimiter;
    private final VendorSettings settings;

    public VendorCallGuard(VendorRateLimiter rateLimiter, LightMoveProperties properties) {
        this.rateLimiter = rateLimiter;
        this.settings = properties.vendor();
    }

    public <T> T call(VendorCall call, Supplier<T> attempt) {
        // The rate itself came from the vendor's spec when its client was built, so there is exactly
        // one place it is configured and no way for a caller to pass a different one by mistake.
        if (!rateLimiter.tryAcquire(call.vendor(), settings.permitMaxWait())) {
            throw new VendorException(call, VendorFailure.of(VendorFailureKind.RATE_LIMITED),
                    "no permit within " + settings.permitMaxWait());
        }

        try {
            return attempt.get();
        } catch (VendorResponseFailure classified) {
            // The status handler knew what went wrong but not what was being asked; complete it.
            throw new VendorException(call, classified.getFailure(), classified);
        } catch (RestClientException noAnswer) {
            throw new VendorException(call, VendorFailure.of(VendorFailureKind.of(noAnswer)), noAnswer);
        }
    }
}
