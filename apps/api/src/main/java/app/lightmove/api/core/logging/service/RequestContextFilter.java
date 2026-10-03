package app.lightmove.api.core.logging.service;

import app.lightmove.api.core.security.model.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the caller's user and workspace ids in the MDC, so Cloud Logging can answer "what did this
 * user / tenant do". Sits inside the security chain right after the bearer token is read — the
 * correlation filter runs before anything is authenticated — and ahead of authorisation, so a
 * refusal carries whose it was.
 *
 * <p>Never a {@code @Component}: Boot would register it as a servlet filter too. Clears nothing;
 * {@link CorrelationIdFilter} owns the MDC.
 */
public class RequestContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            MDC.put(CorrelationId.USER_ID_KEY, principal.userId().toString());
            if (principal.workspaceId() != null) {
                MDC.put(CorrelationId.WORKSPACE_ID_KEY, principal.workspaceId().toString());
            }
        }
        chain.doFilter(request, response);
    }
}
