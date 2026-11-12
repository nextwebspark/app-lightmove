package app.lightmove.api.core.ratelimit.service;

import java.time.Duration;

/**
 * Port for "may this caller do this again yet?". The buckets are in local memory — correct for one instance only;
 * a second doubles the limit, so a Redis-backed bean is the swap.
 */
public interface RateLimiter {

    /**
     * @return true if the caller is within budget and the attempt has been counted; false if they
     *         have exhausted it and the request should be refused.
     */
    boolean tryAcquire(String key, int limit, Duration window);
}
