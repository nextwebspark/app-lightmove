package app.lightmove.api.core.ratelimit.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * In-memory token buckets, one per key, evicted when idle.
 *
 * <p>Caffeine rather than a plain map for a specific reason: the keys are attacker-controlled (an IP,
 * an email), so an unbounded map is itself a denial-of-service — a few million distinct addresses and
 * the process runs out of heap defending itself. Bounded size plus idle expiry makes the limiter's own
 * memory use a constant.
 *
 * <p><b>Single-instance only.</b> See {@link RateLimiter}.
 */
@Component
public class Bucket4jRateLimiter implements RateLimiter {

    private static final int MAX_TRACKED_KEYS = 100_000;

    /**
     * Buckets outlive their window so that a caller who exhausts their budget cannot reset it by
     * pausing for exactly the eviction interval and coming back.
     */
    private static final Duration IDLE_EVICTION = Duration.ofHours(2);

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(MAX_TRACKED_KEYS)
            .expireAfterAccess(IDLE_EVICTION)
            .build();

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        Bucket bucket = buckets.get(key, ignored -> newBucket(limit, window));
        return bucket.tryConsume(1);
    }

    /**
     * A greedy refill drips tokens back continuously rather than restoring the whole allowance on a
     * fixed boundary. That closes the burst at the seam of a fixed window, where an attacker spends
     * the full budget at the end of one window and the full budget again at the start of the next.
     */
    private static Bucket newBucket(int limit, Duration window) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit)
                        .refillGreedy(limit, window)
                        .build())
                .build();
    }
}
