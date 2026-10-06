package app.lightmove.api.core.security.apikey;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PublicApiSettings;
import app.lightmove.api.core.ratelimit.service.RateLimiter;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** The public API's two budgets, both spent before a key is looked up: one per caller IP, one per key. */
@Component
public class PublicApiBudget {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimiter limiter;
    private final PublicApiSettings settings;

    public PublicApiBudget(RateLimiter limiter, LightMoveProperties properties) {
        this.limiter = limiter;
        this.settings = properties.publicApi();
    }

    public void spendForIp(String ip) {
        spend("public-api:ip:" + ip, settings.requestsPerMinutePerIp());
    }

    /** Keyed on the secret's hash, so a throttled key costs no query and an unknown one is counted too. */
    public void spendForKey(String tokenHash) {
        spend("public-api:key:" + tokenHash, settings.requestsPerMinute());
    }

    private void spend(String bucket, int perMinute) {
        if (!limiter.tryAcquire(bucket, perMinute, WINDOW)) {
            throw new ApiKeyThrottledException(Math.max(1, -Math.floorDiv(-WINDOW.toSeconds(), perMinute)));
        }
    }
}
