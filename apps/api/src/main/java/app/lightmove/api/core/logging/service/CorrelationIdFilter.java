package app.lightmove.api.core.logging.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stamps every request with a correlation id — reusing the caller's {@code X-Correlation-Id} if it
 * sent one, so a trace survives across service boundaries — and echoes it back on the response.
 *
 * <p>Ordered first: a request that fails inside a later filter still needs to be findable in the logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** Long enough to be unique in practice, short enough for a user to read down the phone. */
    private static final int ID_LENGTH = 16;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CorrelationId.HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString().replace("-", "").substring(0, ID_LENGTH);
        }

        CorrelationId.set(correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            // Threads are pooled. An id left behind would be inherited by whichever unrelated request
            // picks the thread up next, quietly attributing its logs to the wrong trace.
            CorrelationId.clear();
        }
    }
}
