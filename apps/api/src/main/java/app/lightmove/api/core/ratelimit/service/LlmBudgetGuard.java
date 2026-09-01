package app.lightmove.api.core.ratelimit.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.LlmRateLimitSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Caps how often one user may spend billed model time.
 *
 * <p>Every endpoint that calls Vertex has authentication as its only other gate, so without this an
 * authenticated caller can loop one in a script and run up the project's GCP bill.
 *
 * <p><b>It counts requests, not billed calls.</b> One request can become several: a structured prompt
 * spends up to {@code lightmove.llm.answer-repair-attempts} extra calls re-asking an answer that did
 * not fit. Ten requests a minute can therefore cost more than ten calls, which matters the day
 * somebody tunes these numbers against a GCP bill. A coarse brake to stop a runaway, not a meter.
 *
 * <p>Keyed by user id alone, unlike {@link RateLimitGuard}, which checks an IP budget and an email
 * budget because it guards the pre-auth flows where neither identifies a caller on its own. Here the
 * caller is already authenticated, so there is exactly one honest key. That difference is why this is
 * a separate component rather than more methods on that one: the two answer different questions and
 * record different things — an exhausted login budget is a security event worth an audit row, an
 * exhausted model budget is a cost control.
 */
@Component
public class LlmBudgetGuard {

    private final RateLimiter limiter;
    private final LlmRateLimitSettings settings;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public LlmBudgetGuard(RateLimiter limiter, LightMoveProperties properties) {
        this.limiter = limiter;
        this.settings = properties.llm().rateLimit();
    }

    /**
     * Spends one of this user's shortlist calls for the current minute.
     *
     * @throws ApiException RATE_LIMITED when they have none left
     */
    public void requireShortlistBudget(UUID userId) {
        requireBudget("shortlist", userId, settings.shortlistRequestsPerMinute());
    }

    /**
     * Spends one of this user's embedding calls for the current minute.
     *
     * @throws ApiException RATE_LIMITED when they have none left
     */
    public void requireEmbeddingBudget(UUID userId) {
        requireBudget("embed", userId, settings.embedRequestsPerMinute());
    }

    /**
     * Spends one call from a per-user, per-minute budget, or refuses the request.
     *
     * @param budgetName the meter this call is counted against, not the endpoint that made it — two
     *                   endpoints sharing a name deliberately share a budget
     */
    private void requireBudget(String budgetName, UUID userId, int callsPerMinute) {
        if (!settings.enabled()) {
            return;
        }
        boolean isWithinBudget = limiter.tryAcquire(
                "llm-%s:user:%s".formatted(budgetName, userId), callsPerMinute, Duration.ofMinutes(1));
        if (!isWithinBudget) {
            throw ApiException.of(ErrorCode.RATE_LIMITED);
        }
    }
}
