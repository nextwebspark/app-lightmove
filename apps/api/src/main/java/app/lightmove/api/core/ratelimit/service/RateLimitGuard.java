package app.lightmove.api.core.ratelimit.service;

import app.lightmove.api.core.audit.constant.SecurityEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.RateLimitSettings;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.security.service.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Applies the rate limits on the endpoints strangers can reach — the auth routes, and the in-app
 * feedback reporter — and records it when someone hits one.
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
    private final ClientIpResolver clientIpResolver;
    private final RateLimitSettings config;

    public RateLimitGuard(RateLimiter limiter, AuditService audit, ClientIpResolver clientIpResolver,
                          LightMoveProperties properties) {
        this.limiter = limiter;
        this.audit = audit;
        this.clientIpResolver = clientIpResolver;
        this.config = properties.auth().rateLimit();
    }

    public void checkLogin(String email, HttpServletRequest request) {
        checkRateLimit("login", email, request, config.loginAttemptsPerMinute(), Duration.ofMinutes(1));
    }

    public void checkSignup(String email, HttpServletRequest request) {
        checkRateLimit("signup", email, request, config.signupAttemptsPerHour(), Duration.ofHours(1));
    }

    public void checkVerificationResend(String email, HttpServletRequest request) {
        checkRateLimit("verify-resend", email, request, config.verificationResendsPerHour(), Duration.ofHours(1));
    }

    /**
     * Guards the reset <i>request</i> only. Redeeming is deliberately unlimited: the 256-bit token is
     * the credential and cannot be guessed, and a budget there would let an attacker spend a victim's
     * redemption attempts and lock them out of their own reset.
     */
    public void checkPasswordResetRequest(String email, HttpServletRequest request) {
        checkRateLimit("password-reset", email, request, config.passwordResetRequestsPerHour(), Duration.ofHours(1));
    }

    /**
     * Guards the current-password check in Settings → Security. This is the only brake on guessing it:
     * a wrong attempt here deliberately does not feed the login lockout counter, because the caller
     * already holds a live session and locking the account would only lock its owner out.
     */
    public void checkPasswordChange(String email, HttpServletRequest request) {
        checkRateLimit("password-change", email, request, config.passwordChangeAttemptsPerHour(), Duration.ofHours(1));
    }

    /**
     * The same two-budget check for a route that is not an auth route, with its own limit and its own
     * on/off switch.
     *
     * <p>Called directly rather than through a {@code check*} method of its own because the caller
     * owns the numbers: the feedback endpoint's budget lives under {@code lightmove.feedback}, not
     * under {@code lightmove.auth.rate-limit}, and reading the auth toggle here would let switching
     * the auth limiter off for a staging environment quietly unfence an anonymous write endpoint.
     *
     * @param subjectKind what {@code subject} identifies, for the audit trail — "email", "reporter".
     * @param subject     the second budget's key; blank is a valid key, and is the anonymous bucket.
     */
    public void check(String action, String subjectKind, String subject, HttpServletRequest request,
                      int limit, Duration window) {
        applyBudgets(action, subjectKind, subject, request, limit, window);
    }

    private void checkRateLimit(String action, String email, HttpServletRequest request, int limit, Duration window) {
        if (!config.enabled()) {
            return;
        }
        applyBudgets(action, "email", email, request, limit, window);
    }

    private void applyBudgets(String action, String subjectKind, String subject,
                              HttpServletRequest request, int limit, Duration window) {
        String ip = clientIp(request);
        String normalisedSubject = subject == null ? "" : subject.trim().toLowerCase(Locale.ROOT);

        // Both are consumed, not short-circuited: an attempt should count against the account it
        // targeted even when the IP budget is what refused it, or an attacker could exhaust one
        // account's budget for free by first tripping their own IP limit.
        boolean withinIpBudget = limiter.tryAcquire("%s:ip:%s".formatted(action, ip), limit, window);
        boolean withinSubjectBudget = limiter.tryAcquire(
                "%s:%s:%s".formatted(action, subjectKind, normalisedSubject), limit, window);

        if (withinIpBudget && withinSubjectBudget) {
            return;
        }

        audit.event(SecurityEventType.RATE_LIMIT_EXCEEDED)
                .failed()
                .from(request)
                .detail("action", action)
                // Which budget ran out distinguishes stuffing (ip) from a distributed attack on a
                // single account (email) — the first thing an investigator wants to know.
                .detail("exhausted", !withinIpBudget ? "ip" : subjectKind)
                .record();

        throw ApiException.of(ErrorCode.RATE_LIMITED);
    }

    /**
     * The per-IP budget is only as honest as this value. It used to read the leftmost
     * {@code X-Forwarded-For} entry, which the caller supplies — so a fresh header meant a fresh bucket,
     * every request, and the per-IP limit stopped nobody. See {@link ClientIpResolver}.
     */
    private String clientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }
}
