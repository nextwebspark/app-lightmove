package app.lightmove.api.core.ratelimit.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.LlmRateLimitSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Caps how often one user may spend billed model time. Every endpoint that calls Vertex has
 * authentication as its only other gate, so without this an authenticated caller can loop one in a
 * script and run up the project's GCP bill.
 *
 * <p><b>It counts requests, not billed calls.</b> One request can become several — a structured prompt
 * spends up to {@code lightmove.llm.answer-repair-attempts} extra calls re-asking an answer that did
 * not fit — so ten requests a minute can cost more than ten calls. A coarse brake, not a meter.
 *
 * <p>Keyed by user id alone, unlike {@link RateLimitGuard}, which guards the pre-auth flows where
 * neither an IP nor an email identifies a caller on its own. An exhausted login budget is a security
 * event worth an audit row; an exhausted model budget is a cost control.
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
