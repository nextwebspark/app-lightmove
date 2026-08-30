package app.lightmove.api.core.vendor.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Paces outbound calls to a vendor's published rate cap, waiting for a permit rather than refusing.
 *
 * <p>The mirror image of {@code RateLimiter}, and deliberately a different shape. Inbound, the answer
 * to "may this caller go again?" is yes or no and a no is a 429 we send. Outbound, the answer is
 * "not yet" — a call held back for 80 ms costs nothing, where the 429 we would otherwise have earned
 * costs a round trip and a retry. So this one waits.
 *
 * <p><b>The wait is bounded, and that bound is the point.</b> Bucket4j's {@code consume} reserves
 * tokens with no ceiling: the bucket goes negative and the caller parks for the full deficit. With
 * virtual threads there is no pool imposing backpressure, so a burst of a few thousand callers
 * against an 18/second bucket parks the last of them for minutes, long after whoever asked has gone.
 * {@code tryConsume} with a maximum wait fails fast instead, and a fast failure is a retryable one.
 *
 * <p><b>Single instance only.</b> The buckets live in this process, so N replicas mean N times the
 * configured rate reaches the vendor. Inbound that would merely be over-permissive; outbound it is
 * how an API key gets suspended. Swap for a shared counter before running more than one instance.
 */
@Component
public class VendorRateLimiter {

    /** Vendors are ours, not attacker-supplied, so this is a leak guard rather than a defence. */
    private static final int MAX_TRACKED_VENDORS = 64;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(MAX_TRACKED_VENDORS)
            .build();

    /**
     * @param requestsPerSecond the vendor's cap; zero or less leaves the call unpaced
     * @return true once a permit is held; false if none came free within {@code maxWait}
     */
    public boolean tryAcquire(String vendor, int requestsPerSecond, Duration maxWait) {
        if (requestsPerSecond <= 0) {
            return true;
        }
        Bucket bucket = buckets.get(vendor, ignored -> newBucket(requestsPerSecond));
        try {
            return bucket.asBlocking().tryConsume(1, maxWait);
        } catch (InterruptedException ex) {
            // Someone is shutting us down or the request was abandoned. Restore the flag rather than
            // swallowing it, and report no permit — the caller turns that into a retryable failure.
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * A greedy refill drips tokens back continuously instead of restoring the whole second's
     * allowance on a boundary, which is what stops a burst at the seam of two windows from arriving
     * at the vendor as double the rate they published.
     */
    private static Bucket newBucket(int requestsPerSecond) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerSecond)
                        .refillGreedy(requestsPerSecond, Duration.ofSeconds(1))
                        .build())
                .build();
    }
}
