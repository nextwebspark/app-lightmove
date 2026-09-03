package app.lightmove.api.core.resilience.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Outbound pacing, one bucket per vendor.
 *
 * <p>The inbound limiter refuses a caller who is over budget; this one makes a caller wait, because
 * the budget here is a vendor's published cap and the right answer to reaching it is to go slower
 * rather than to drop the work. Vendors are a fixed, tiny set named at startup, so unlike the inbound
 * limiter's attacker-supplied keys this map needs no eviction.
 */
@Component
public class VendorRateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void pace(String vendor, int requestsPerSecond) {
        buckets.computeIfAbsent(vendor, ignored -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerSecond)
                        .refillGreedy(requestsPerSecond, Duration.ofSeconds(1))
                        .build())
                .build());
    }

    /** True once this call holds a permit; false if none came free inside {@code maxWait}. */
    public boolean tryAcquire(String vendor, Duration maxWait) {
        Bucket bucket = buckets.get(vendor);
        if (bucket == null) {
            return true;
        }
        try {
            return bucket.asBlocking().tryConsume(1, maxWait);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
