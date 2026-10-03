package app.lightmove.api.core.logging.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stamps every request with a correlation id — reusing the caller's {@code X-Correlation-Id} if it
 * is well-formed, so a trace survives across service boundaries — and echoes it back on the response.
 * Where Cloud Run passed a trace, puts it in the MDC too, so every line nests under Cloud Run's request
 * entry in Logs Explorer.
 *
 * <p>Ordered first: a request that fails inside a later filter still needs to be findable in the logs.
 * Being first also makes this filter the owner of the request's MDC — see the {@code finally}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** Long enough to be unique in practice, short enough for a user to read down the phone. */
    private static final int ID_LENGTH = 16;

    /**
     * {@code app_lm_audit_event.correlation_id} is {@code varchar(64)}. An unbounded inbound id once made
     * every audit insert of that request fail — swallowed by design — so any caller, signed in or not,
     * could erase their own LOGIN_FAILED and ACCOUNT_LOCKED rows with one header.
     */
    private static final Pattern ACCEPTED_INBOUND_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final String gcpProjectId;

    /** Cloud Run sets no {@code GOOGLE_CLOUD_PROJECT}; deploy.sh does. Absent, no trace field is written. */
    public CorrelationIdFilter(@Value("${GOOGLE_CLOUD_PROJECT:}") String gcpProjectId) {
        this.gcpProjectId = gcpProjectId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CorrelationId.HEADER);
        if (correlationId == null || !ACCEPTED_INBOUND_ID.matcher(correlationId).matches()) {
            correlationId = UUID.randomUUID().toString().replace("-", "").substring(0, ID_LENGTH);
        }

        CorrelationId.set(correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        putTrace(request);

        try {
            chain.doFilter(request, response);
        } finally {
            // Threads are pooled. A key left behind would be inherited by whichever unrelated request
            // picks the thread up next. Cleared here rather than by each filter that adds one, because the
            // request-completion line is written by an inner filter after the others have returned.
            MDC.clear();
        }
    }

    private void putTrace(HttpServletRequest request) {
        if (gcpProjectId == null || gcpProjectId.isBlank()) {
            return;
        }
        CloudTraceContext.parse(request.getHeader("traceparent"), request.getHeader("X-Cloud-Trace-Context"))
                .ifPresent(trace -> {
                    MDC.put(CorrelationId.TRACE_KEY, "projects/" + gcpProjectId + "/traces/" + trace.traceId());
                    MDC.put(CorrelationId.SPAN_ID_KEY, trace.spanIdHex());
                    MDC.put(CorrelationId.TRACE_SAMPLED_KEY, Boolean.toString(trace.sampled()));
                });
    }
}
