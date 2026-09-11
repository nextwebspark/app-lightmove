package app.lightmove.api.core.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sends a page opened on any hostname other than {@code lightmove.web.base-url}'s to that one.
 *
 * <p>Cloud Run answers on two run.app hostnames, and OAuth always returns to the base URL. A sign-in
 * started on the other one finds neither its state cookie nor its popup handshake there, and the
 * popup renders the whole app inside itself.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CanonicalOriginRedirectFilter extends OncePerRequestFilter {

    private final String baseUrl;
    private final String canonicalHost;

    public CanonicalOriginRedirectFilter(LightMoveProperties properties) {
        this.baseUrl = properties.web().baseUrl();
        this.canonicalHost = URI.create(baseUrl).getHost();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        return !(HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method))
                || !SpaRequestPaths.isSpaPath(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getServerName().equalsIgnoreCase(canonicalHost)) {
            chain.doFilter(request, response);
            return;
        }

        String query = request.getQueryString();
        response.sendRedirect(baseUrl + request.getRequestURI() + (query == null ? "" : "?" + query));
    }
}
