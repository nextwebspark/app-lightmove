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

    /** @throws ApiException RATE_LIMITED when this user has no shortlist calls left this minute */
    public void requireShortlistBudget(UUID userId) {
        requireBudget("shortlist", userId, settings.shortlistRequestsPerMinute());
    }

    /** @throws ApiException RATE_LIMITED when this user has no embedding calls left this minute */
    public void requireEmbeddingBudget(UUID userId) {
        requireBudget("embed", userId, settings.embedRequestsPerMinute());
    }

    /**
     * Spends one of this user's column-mapping calls — its own meter, so a large import cannot eat
     * the shortlist a consultant is about to run. Keyed as the prompt id the call is logged under.
     *
     * @throws ApiException RATE_LIMITED when they have none left
     */
    public void requireColumnMappingBudget(UUID userId) {
        requireBudget("import-column-mapping", userId, settings.shortlistRequestsPerMinute());
    }

    /**
     * Spends one of this user's position-extraction calls, the model call behind step one's "Read
     * from document".
     *
     * <p>Its own meter, sized off the shortlist's number for the same reason column mapping's is: an
     * extraction must not eat the shortlist budget a consultant is about to spend, and vice versa.
     *
     * @throws ApiException RATE_LIMITED when they have none left
     */
    public void requirePositionExtractionBudget(UUID userId) {
        requireBudget("position-extract", userId, settings.shortlistRequestsPerMinute());
    }

    /**
     * Spends one of this user's mandate-context-extraction calls — its own meter, so step two's
     * "Read from document" cannot eat step one's or step four's budget, or vice versa.
     *
     * @throws ApiException RATE_LIMITED when they have none left
     */
    public void requireContextExtractionBudget(UUID userId) {
        requireBudget("context-extract", userId, settings.shortlistRequestsPerMinute());
    }

    /**
     * Spends one of this user's compensation-extraction calls — its own meter, for the same reason
     * {@link #requireContextExtractionBudget} is.
     *
     * @throws ApiException RATE_LIMITED when they have none left
     */
    public void requireCompensationExtractionBudget(UUID userId) {
        requireBudget("compensation-extract", userId, settings.shortlistRequestsPerMinute());
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
