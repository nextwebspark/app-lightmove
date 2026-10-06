package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.audit.constant.SecurityEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.McpSettings;
import app.lightmove.api.core.config.RateLimitSettings;
import app.lightmove.api.core.ratelimit.service.RateLimiter;
import app.lightmove.api.core.security.service.ClientIpResolver;
import app.lightmove.api.core.security.token.Tokens;
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
 * Budgets for the authorize, token and registration endpoints, spent before anything is read: per IP on all three, and
 * per client on the token endpoint, where a stolen refresh token or a guessed code would be tried. Registration needs
 * nothing but a request, so its budget is counted in hours.
 */
public class OAuthRateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Duration REGISTRATION_WINDOW = Duration.ofHours(1);

    private final String authorizeEndpoint;
    private final String tokenEndpoint;
    private final String registrationEndpoint;
    private final McpSettings budgets;
    private final RateLimitSettings rateLimit;
    private final RateLimiter limiter;
    private final ClientIpResolver clientIps;
    private final AuditService audit;

    public OAuthRateLimitFilter(String authorizeEndpoint, String tokenEndpoint, String registrationEndpoint,
                                McpSettings budgets, RateLimitSettings rateLimit, RateLimiter limiter,
                                ClientIpResolver clientIps, AuditService audit) {
        this.authorizeEndpoint = authorizeEndpoint;
        this.tokenEndpoint = tokenEndpoint;
        this.registrationEndpoint = registrationEndpoint;
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
        boolean registration = path.equals(registrationEndpoint);
        if (!rateLimit.enabled() || (!authorize && !token && !registration)) {
            chain.doFilter(request, response);
            return;
        }

        String ip = clientIps.resolve(request);
        boolean withinBudget;
        if (authorize) {
            withinBudget = limiter.tryAcquire("oauth-authorize:ip:" + ip, budgets.authorizePerMinutePerIp(), WINDOW);
        } else if (registration) {
            withinBudget = limiter.tryAcquire("oauth-register:ip:" + ip, budgets.registerPerHourPerIp(),
                    REGISTRATION_WINDOW);
        } else {
            withinBudget = withinTokenBudget(request, ip);
        }
        if (withinBudget) {
            chain.doFilter(request, response);
            return;
        }

        audit.event(SecurityEventType.RATE_LIMIT_EXCEEDED).failed().from(request)
                .detail("action", authorize ? "oauth-authorize" : registration ? "oauth-register" : "oauth-token")
                .record();
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER,
                Long.toString((registration ? REGISTRATION_WINDOW : WINDOW).toSeconds()));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"temporarily_unavailable\"}");
    }

    /**
     * Both spent, not short-circuited, as the login budgets are. The client's budget is per address too: a public
     * client's id is shared by all its users and needs no secret, so a budget on the id alone was one any stranger
     * could spend to refuse every user of that app.
     */
    private boolean withinTokenBudget(HttpServletRequest request, String ip) {
        boolean withinIp = limiter.tryAcquire("oauth-token:ip:" + ip, budgets.tokenPerMinutePerIp(), WINDOW);
        String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        boolean withinClient = clientId == null || limiter.tryAcquire(
                "oauth-token:client:" + Tokens.hash(clientId) + ":ip:" + ip, budgets.tokenPerMinutePerClient(), WINDOW);
        return withinIp && withinClient;
    }
}
