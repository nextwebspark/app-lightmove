package app.lightmove.api.common.ratelimit;

import java.time.Duration;

/**
 * Port for "may this caller do this again yet?".
 *
 * <p>An interface rather than a direct Bucket4j call because the current implementation holds its
 * buckets in local memory, which is correct for exactly one instance. The moment a second instance
 * runs, each holds its own counters and the effective limit silently doubles. Swapping in a
 * Redis-backed implementation then changes one {@code @Bean} and nothing else.
 */
public interface RateLimiter {

    /**
     * @return true if the caller is within budget and the attempt has been counted; false if they
     *         have exhausted it and the request should be refused.
     */
    boolean tryAcquire(String key, int limit, Duration window);
}
