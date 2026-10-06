package app.lightmove.api.mcp.service;

import app.lightmove.api.core.audit.constant.SecurityEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.McpSettings;
import app.lightmove.api.core.config.RateLimitSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.ratelimit.service.RateLimiter;
import app.lightmove.api.core.security.apikey.PublicApiProblemWriter;
import app.lightmove.api.core.security.service.ClientIpResolver;
import app.lightmove.api.mcp.model.McpCaller;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** MCP calls per grant or key and per address, spent once the caller is known and before the call is read. */
public class McpRateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final McpSettings budgets;
    private final RateLimitSettings rateLimit;
    private final RateLimiter limiter;
    private final ClientIpResolver clientIps;
    private final PublicApiProblemWriter problems;
    private final AuditService audit;

    public McpRateLimitFilter(McpSettings budgets, RateLimitSettings rateLimit, RateLimiter limiter,
                              ClientIpResolver clientIps, PublicApiProblemWriter problems, AuditService audit) {
        this.budgets = budgets;
        this.rateLimit = rateLimit;
        this.limiter = limiter;
        this.clientIps = clientIps;
        this.problems = problems;
        this.audit = audit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!rateLimit.enabled() || authentication == null
                || !(authentication.getPrincipal() instanceof McpCaller caller)) {
            chain.doFilter(request, response);
            return;
        }
        boolean withinIp = limiter.tryAcquire("mcp:ip:" + clientIps.resolve(request),
                budgets.callsPerMinutePerIp(), WINDOW);
        boolean withinCredential = limiter.tryAcquire("mcp:credential:" + caller.credentialId(),
                budgets.callsPerMinutePerCredential(), WINDOW);
        if (withinIp && withinCredential) {
            chain.doFilter(request, response);
            return;
        }
        audit.event(SecurityEventType.RATE_LIMIT_EXCEEDED).failed().from(request)
                .workspace(caller.workspaceId())
                .detail("action", "mcp")
                .detail("credentialKind", caller.credentialKind().name())
                .record();
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(WINDOW.toSeconds()));
        problems.write(request, response, ErrorCode.RATE_LIMITED);
    }
}
