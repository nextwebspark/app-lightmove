package app.lightmove.api.core.vendor.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Paces outbound calls to a vendor's published rate cap, waiting for a permit rather than refusing.
 *
 * <p>The mirror image of {@code RateLimiter}, and deliberately a different shape. Inbound, the answer
 * to "may this caller go again?" is yes or no and a no is a 429 we send. Outbound, the answer is
 * "not yet" — a call held back for 80 ms costs nothing, where the 429 we would otherwise have earned
 * costs a round trip and a retry. So this one waits.
 *
 * <p><b>A vendor is paced from the moment its client is built, and only then.</b>
 * {@link VendorClientFactory} registers the rate off the {@code VendorClientSpec}, which makes the
 * spec the single place that number is written. An earlier draft passed it per call instead, which
 * meant a spec could carry a rate that nothing read — an adapter author who set it and trusted it
 * would have shipped completely unpaced calls, silently.
 *
 * <p>A plain map rather than a bounded cache, unlike the inbound limiter. Its keys are
 * attacker-supplied (an IP, an email) so an unbounded map is itself a denial of service; these are
 * vendor names from our own configuration, a handful at most. Eviction would be the real hazard here:
 * a dropped entry does not merely forget a counter, it silently un-paces the vendor.
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

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Declares how fast this vendor may be called. Called once, when its client is built.
     *
     * @param requestsPerSecond the vendor's published cap; zero or less leaves it unpaced
     */
    public void pace(String vendor, int requestsPerSecond) {
        if (requestsPerSecond <= 0) {
            buckets.remove(vendor);
            return;
        }
        buckets.put(vendor, newBucket(requestsPerSecond));
    }

    /**
     * @return true once a permit is held — including for a vendor nobody paced; false only when a
     *         paced vendor had none free within {@code maxWait}
     */
    public boolean tryAcquire(String vendor, Duration maxWait) {
        Bucket bucket = buckets.get(vendor);
        if (bucket == null) {
            return true;
        }
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
