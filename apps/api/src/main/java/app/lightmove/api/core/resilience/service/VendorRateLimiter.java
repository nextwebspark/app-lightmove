package app.lightmove.api.core.resilience.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Outbound pacing, one bucket per vendor: waits rather than refuses, since the cap is the vendor's. Vendors are a fixed
 * set named at startup, so the map needs no eviction.
 */
@Component
@Slf4j
public class VendorRateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void pace(String vendor, int requestsPerSecond) {
        pace(vendor, requestsPerSecond, Duration.ofSeconds(1));
    }

    /**
     * {@code permits} calls every {@code period}, for a vendor whose cap is slower than one a second. The
     * first registration of a vendor wins, so an adapter pacing itself this way does so before its client
     * is built.
     */
    public void pace(String vendor, int permits, Duration period) {
        buckets.computeIfAbsent(vendor, ignored -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(permits)
                        .refillGreedy(permits, period)
                        .build())
                .build());
    }

    /** True once this call holds a permit; false if none came free inside {@code maxWait}. */
    public boolean tryAcquire(String vendor, Duration maxWait) {
        Bucket bucket = buckets.get(vendor);
        if (bucket == null) {
            // Nothing registered this vendor, so the call proceeds unpaced. That is a wiring mistake —
            // the name here and the one VendorClientSpec paces with are two strings nothing ties
            // together — and it is silent until an invoice arrives, so say so.
            log.warn("No rate limit registered for vendor '{}' — calls to it are unpaced", vendor);
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
