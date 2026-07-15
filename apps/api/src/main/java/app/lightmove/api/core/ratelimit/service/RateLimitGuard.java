package app.lightmove.api.core.ratelimit.service;

import app.lightmove.api.core.audit.constant.AuditEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.security.service.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Applies the auth endpoints' rate limits, and records it when someone hits one.
 *
 * <p>Each guard checks <b>two</b> budgets, because the two attacks have different shapes:
 *
 * <ul>
 *   <li><b>By IP</b> — one host trying many accounts. Credential stuffing.
 *   <li><b>By email</b> — many hosts trying one account. A botnet spreading attempts across thousands
 *       of addresses defeats an IP limit entirely; only the per-account budget sees it.
 * </ul>
 *
 * <p>Deliberately not a servlet filter: the email lives in the request body, and a filter that parses
 * the body has to buffer and re-serve the stream. Calling this from the service, where the parsed
 * command is already in hand, is simpler and easier to test.
 */
@Component
public class RateLimitGuard {

    private final RateLimiter limiter;
    private final AuditService audit;
    private final ClientIpResolver clientIps;
    private final LightMoveProperties.Auth.RateLimit config;

    public RateLimitGuard(RateLimiter limiter, AuditService audit, ClientIpResolver clientIps,
                          LightMoveProperties properties) {
        this.limiter = limiter;
        this.audit = audit;
        this.clientIps = clientIps;
        this.config = properties.auth().rateLimit();
    }

    public void checkLogin(String email, HttpServletRequest request) {
        check("login", email, request, config.loginAttemptsPerMinute(), Duration.ofMinutes(1));
    }

    public void checkSignup(String email, HttpServletRequest request) {
        check("signup", email, request, config.signupAttemptsPerHour(), Duration.ofHours(1));
    }

    public void checkVerificationResend(String email, HttpServletRequest request) {
        check("verify-resend", email, request, config.verificationResendsPerHour(), Duration.ofHours(1));
    }

    private void check(String action, String email, HttpServletRequest request, int limit, Duration window) {
        if (!config.enabled()) {
            return;
        }

        String ip = clientIp(request);
        String normalisedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);

        // Both are consumed, not short-circuited: an attempt should count against the account it
        // targeted even when the IP budget is what refused it, or an attacker could exhaust one
        // account's budget for free by first tripping their own IP limit.
        boolean withinIpBudget = limiter.tryAcquire("%s:ip:%s".formatted(action, ip), limit, window);
        boolean withinEmailBudget = limiter.tryAcquire("%s:email:%s".formatted(action, normalisedEmail), limit, window);

        if (withinIpBudget && withinEmailBudget) {
            return;
        }

        audit.event(AuditEventType.RATE_LIMIT_EXCEEDED)
                .failed()
                .from(request)
                .detail("action", action)
                // Which budget ran out distinguishes stuffing (ip) from a distributed attack on a
                // single account (email) — the first thing an investigator wants to know.
                .detail("exhausted", !withinIpBudget ? "ip" : "email")
                .record();

        throw ApiException.of(ErrorCode.RATE_LIMITED);
    }

    /**
     * The per-IP budget is only as honest as this value. It used to read the leftmost
     * {@code X-Forwarded-For} entry, which the caller supplies — so a fresh header meant a fresh bucket,
     * every request, and the per-IP limit stopped nobody. See {@link ClientIpResolver}.
     */
    private String clientIp(HttpServletRequest request) {
        return clientIps.resolve(request);
    }
}
