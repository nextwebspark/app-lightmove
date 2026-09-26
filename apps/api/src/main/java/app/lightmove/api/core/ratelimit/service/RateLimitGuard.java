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
 * The auth endpoints' rate limits, each checking a per-IP budget (credential stuffing) and a per-email
 * one (a botnet on one account). Not a filter: the email lives in the request body.
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

    /** The request only: a budget on redeeming would let an attacker lock a victim out of their own reset. */
    public void checkPasswordResetRequest(String email, HttpServletRequest request) {
        checkRateLimit("password-reset", email, request, config.passwordResetRequestsPerHour(), Duration.ofHours(1));
    }

    /** The only brake on guessing the current password, which deliberately does not feed the lockout counter. */
    public void checkPasswordChange(String email, HttpServletRequest request) {
        checkRateLimit("password-change", email, request, config.passwordChangeAttemptsPerHour(), Duration.ofHours(1));
    }

    /** Blast radius: a stolen access token must not mint long-lived extension refresh tokens repeatedly. */
    public void checkExtensionPairing(String email, HttpServletRequest request) {
        checkRateLimit("extension-pairing", email, request, config.extensionPairingsPerHour(),
                config.extensionPairingsPerHourPerIp(), Duration.ofHours(1));
    }

    public void checkOnboardingCompanySearch(String email, HttpServletRequest request) {
        checkRateLimit("onboarding-company-search", email, request, config.onboardingCompanySearchesPerMinute(),
                Duration.ofMinutes(1));
    }

    public void checkWorkspaceCreation(String email, HttpServletRequest request) {
        checkRateLimit("workspace-creation", email, request, config.workspaceCreationsPerHour(),
                config.workspaceCreationsPerHourPerIp(), Duration.ofHours(1));
    }

    private void checkRateLimit(String action, String email, HttpServletRequest request, int limit, Duration window) {
        checkRateLimit(action, email, request, limit, limit, window);
    }

    private void checkRateLimit(String action, String email, HttpServletRequest request,
                                int emailLimit, int ipLimit, Duration window) {
        if (!config.enabled()) {
            return;
        }

        String ip = clientIp(request);
        String normalisedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);

        // Both consumed, not short-circuited: an attempt counts against its account even when the IP
        // budget refused it.
        boolean withinIpBudget = limiter.tryAcquire("%s:ip:%s".formatted(action, ip), ipLimit, window);
        boolean withinEmailBudget =
                limiter.tryAcquire("%s:email:%s".formatted(action, normalisedEmail), emailLimit, window);

        if (withinIpBudget && withinEmailBudget) {
            return;
        }

        audit.event(SecurityEventType.RATE_LIMIT_EXCEEDED)
                .failed()
                .from(request)
                .detail("action", action)
                .detail("exhausted", !withinIpBudget ? "ip" : "email")
                .record();

        throw ApiException.of(ErrorCode.RATE_LIMITED);
    }

    /** Never the leftmost {@code X-Forwarded-For}: a caller-supplied value made every request a fresh bucket. */
    private String clientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }
}
