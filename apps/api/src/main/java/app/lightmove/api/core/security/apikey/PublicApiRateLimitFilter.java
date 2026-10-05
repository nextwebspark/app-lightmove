package app.lightmove.api.core.security.apikey;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.ratelimit.service.RateLimiter;
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

/**
 * One budget per key across every public route. Built by the public chain, never a bean: as a bean Boot
 * would also register it on every request the servlet container serves.
 */
public class PublicApiRateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimiter limiter;
    private final PublicApiProblemWriter problems;
    private final int requestsPerMinute;
    private final String retryAfterSeconds;

    public PublicApiRateLimitFilter(RateLimiter limiter, PublicApiProblemWriter problems, int requestsPerMinute) {
        this.limiter = limiter;
        this.problems = problems;
        this.requestsPerMinute = requestsPerMinute;
        this.retryAfterSeconds = Long.toString(Math.max(1, -Math.floorDiv(-WINDOW.toSeconds(), requestsPerMinute)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ApiKeyPrincipal key
                && !limiter.tryAcquire("public-api:key:" + key.keyId(), requestsPerMinute, WINDOW)) {
            response.setHeader(HttpHeaders.RETRY_AFTER, retryAfterSeconds);
            problems.write(request, response, ErrorCode.RATE_LIMITED);
            return;
        }
        chain.doFilter(request, response);
    }
}
