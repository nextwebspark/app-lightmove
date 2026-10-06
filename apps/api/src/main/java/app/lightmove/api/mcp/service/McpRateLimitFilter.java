package app.lightmove.api.mcp.service;

import app.lightmove.api.core.audit.constant.SecurityEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.McpSettings;
import app.lightmove.api.core.config.RateLimitSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.ratelimit.service.RateLimiter;
import app.lightmove.api.core.security.apikey.PublicApiProblemWriter;
import app.lightmove.api.mcp.model.McpCaller;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * MCP calls per grant or key, spent once the caller is known. Never per address: hosted clients (claude.ai, ChatGPT)
 * call from shared egress, so an address budget would let one tenant's agent refuse every other tenant's.
 */
@RequiredArgsConstructor
public class McpRateLimitFilter extends OncePerRequestFilter {

    public static final Duration WINDOW = Duration.ofMinutes(1);

    private final McpSettings budgets;
    private final RateLimitSettings rateLimit;
    private final RateLimiter limiter;
    private final PublicApiProblemWriter problems;
    private final AuditService audit;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!rateLimit.enabled() || authentication == null
                || !(authentication.getPrincipal() instanceof McpCaller caller)) {
            chain.doFilter(request, response);
            return;
        }
        if (limiter.tryAcquire("mcp:credential:" + caller.credentialId(),
                budgets.callsPerMinutePerCredential(), WINDOW)) {
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
