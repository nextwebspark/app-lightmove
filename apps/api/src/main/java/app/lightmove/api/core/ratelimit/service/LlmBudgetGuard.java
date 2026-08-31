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
 * authenticated caller can loop one in a script and run up the project's GCP bill. A coarse brake,
 * deliberately: it exists to stop a runaway, not to meter usage.
 *
 * <p>Keyed by user id alone, unlike {@link RateLimitGuard}, which checks an IP budget and an email
 * budget because it guards the pre-auth flows where neither identifies a caller on its own. Here the
 * caller is already authenticated, so there is exactly one honest key. That difference is why this is
 * a separate component rather than more methods on that one: the two answer different questions and
 * record different things — a exhausted login budget is a security event worth an audit row, an
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

    public void checkShortlist(UUID userId) {
        check("shortlist", userId, settings.shortlistRequestsPerMinute());
    }

    public void checkEmbed(UUID userId) {
        check("embed", userId, settings.embedRequestsPerMinute());
    }

    /**
     * The column-mapping call behind an import preview. Budgeted with the shortlist call rather than
     * given a number of its own: both are one deliberate click by a person, and a second knob would be
     * a second thing to get wrong for no behaviour anybody wanted.
     */
    public void checkColumnMapping(UUID userId) {
        check("import-mapping", userId, settings.shortlistRequestsPerMinute());
    }

    private void check(String action, UUID userId, int limit) {
        if (!settings.enabled()) {
            return;
        }
        boolean withinBudget = limiter.tryAcquire(
                "llm-%s:user:%s".formatted(action, userId), limit, Duration.ofMinutes(1));
        if (!withinBudget) {
            throw ApiException.of(ErrorCode.RATE_LIMITED);
        }
    }
}
