package app.lightmove.api.core.ratelimit.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.LlmRateLimitSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Caps how often one user may spend billed model time, which authentication alone would not. Counts
 * requests, not billed calls — a coarse brake, not a meter — keyed by user, unlike {@link RateLimitGuard}.
 */
@Component
public class LlmBudgetGuard {

    private final RateLimiter limiter;
    private final LlmRateLimitSettings settings;

    public LlmBudgetGuard(RateLimiter limiter, LightMoveProperties properties) {
        this.limiter = limiter;
        this.settings = properties.llm().rateLimit();
    }

    /**
     * Spends one of this user's calls against {@code budget}'s meter, or refuses the request.
     *
     * @throws ApiException RATE_LIMITED when they have none left
     */
    public void require(LlmBudget budget, UUID userId) {
        if (!settings.enabled()) {
            return;
        }
        boolean isWithinBudget = limiter.tryAcquire("llm-%s:user:%s".formatted(budget.meter(), userId),
                budget.callsPerMinute(settings), Duration.ofMinutes(1));
        if (!isWithinBudget) {
            throw ApiException.of(ErrorCode.RATE_LIMITED);
        }
    }
}
