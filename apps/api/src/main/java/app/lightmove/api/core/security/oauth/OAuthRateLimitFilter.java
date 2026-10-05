package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.audit.constant.SecurityEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.McpSettings;
import app.lightmove.api.core.config.RateLimitSettings;
import app.lightmove.api.core.ratelimit.service.RateLimiter;
import app.lightmove.api.core.security.service.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Budgets for the authorize and token endpoints, spent before anything is read: per IP on both, and per client on the
 * token endpoint, where a stolen refresh token or a guessed code would be tried.
 */
public class OAuthRateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final String authorizeEndpoint;
    private final String tokenEndpoint;
    private final McpSettings budgets;
    private final RateLimitSettings rateLimit;
    private final RateLimiter limiter;
    private final ClientIpResolver clientIps;
    private final AuditService audit;

    public OAuthRateLimitFilter(String authorizeEndpoint, String tokenEndpoint, McpSettings budgets,
                                RateLimitSettings rateLimit, RateLimiter limiter, ClientIpResolver clientIps,
                                AuditService audit) {
        this.authorizeEndpoint = authorizeEndpoint;
        this.tokenEndpoint = tokenEndpoint;
        this.budgets = budgets;
        this.rateLimit = rateLimit;
        this.limiter = limiter;
        this.clientIps = clientIps;
        this.audit = audit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean authorize = path.equals(authorizeEndpoint);
        boolean token = path.equals(tokenEndpoint);
        if (!rateLimit.enabled() || (!authorize && !token)) {
            chain.doFilter(request, response);
            return;
        }

        String ip = clientIps.resolve(request);
        boolean withinBudget = authorize
                ? limiter.tryAcquire("oauth-authorize:ip:" + ip, budgets.authorizePerMinutePerIp(), WINDOW)
                : withinTokenBudget(request, ip);
        if (withinBudget) {
            chain.doFilter(request, response);
            return;
        }

        audit.event(SecurityEventType.RATE_LIMIT_EXCEEDED).failed().from(request)
                .detail("action", authorize ? "oauth-authorize" : "oauth-token")
                .record();
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(WINDOW.toSeconds()));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"temporarily_unavailable\"}");
    }

    /** Both spent, not short-circuited, as the login budgets are: an attempt counts against its client either way. */
    private boolean withinTokenBudget(HttpServletRequest request, String ip) {
        boolean withinIp = limiter.tryAcquire("oauth-token:ip:" + ip, budgets.tokenPerMinutePerIp(), WINDOW);
        String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        boolean withinClient = clientId == null
                || limiter.tryAcquire("oauth-token:client:" + clientId, budgets.tokenPerMinutePerClient(), WINDOW);
        return withinIp && withinClient;
    }
}
