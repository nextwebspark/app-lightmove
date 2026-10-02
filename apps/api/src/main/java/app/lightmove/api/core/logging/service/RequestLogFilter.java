package app.lightmove.api.core.logging.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import app.lightmove.api.core.config.SpaRequestPaths;
import app.lightmove.api.core.error.service.ClientDisconnects;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * One line per request — {@code method}, {@code route}, {@code status}, {@code durationMs} as top-level
 * JSON fields — the only per-route latency signal production has, and the line per-tenant Log Analytics
 * queries read.
 *
 * <p>INFO whatever the status: a 500's ERROR is {@code GlobalExceptionHandler}'s, and a second one here
 * would count every failure twice. No field is named {@code httpRequest} — Cloud Logging would promote
 * it, and this line would become a second request entry beside Cloud Run's instead of a child of it.
 *
 * <p>Inside {@link CorrelationIdFilter}, so the line carries the correlation id, the trace and the
 * tenant keys the security chain added — that filter clears them only after this one has written.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@Slf4j
public class RequestLogFilter extends OncePerRequestFilter {

    /** nginx's convention for a client that closed the connection before the response. */
    static final int CLIENT_CLOSED_REQUEST = 499;

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMapping;

    public RequestLogFilter(
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    /** Health probes and the SPA's assets would otherwise be most of the log volume, and of the bill. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator") || SpaRequestPaths.isSpaPath(uri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        Throwable escaped = null;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            escaped = ex;
            throw ex;
        } finally {
            String route = routeOf(request);
            Throwable failure = escaped;
            if (request.isAsyncStarted()) {
                // A stream returns here as soon as it starts; its line is written when it ends, with its
                // real lifetime and outcome. The MDC is captured now — the completing thread has none.
                Runnable writeLine = MdcPropagation.wrap(() -> writeLine(request, route, response.getStatus(), started));
                request.getAsyncContext().addListener(new StreamCompletion(request, writeLine));
            } else {
                writeLine(request, route, statusOf(response, failure), started);
            }
        }
    }

    private static int statusOf(HttpServletResponse response, Throwable failure) {
        if (failure == null) {
            return response.getStatus();
        }
        return ClientDisconnects.isDisconnect(failure) ? CLIENT_CLOSED_REQUEST : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    private static void writeLine(HttpServletRequest request, String route, int status, long started) {
        int recorded = ClientDisconnects.isMarkedGone(request) ? CLIENT_CLOSED_REQUEST : status;
        log.info("request",
                kv("method", request.getMethod()),
                kv("route", route),
                kv("status", recorded),
                kv("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)));
    }

    /**
     * The route template, never the raw path: a path carries UUIDs, and every distinct value would be its
     * own metric series. A request refused before dispatch (a 401, a 403) is matched here so it is still
     * attributed to its route; only one no controller answers collapses to {@code /<segment>/<unmatched>}.
     */
    private String routeOf(HttpServletRequest request) {
        String matched = controllerPatternOf(request);
        if (matched != null) {
            return matched;
        }
        RequestMappingHandlerMapping mapping = handlerMapping.getIfAvailable();
        if (mapping != null) {
            try {
                if (mapping.getHandler(request) != null) {
                    matched = controllerPatternOf(request);
                }
            } catch (Exception noMatch) {
                // A wrong method or media type: there is no template to report.
            }
        }
        return matched != null ? matched : unmatched(request.getRequestURI());
    }

    /** Only a controller's pattern counts: the static-resource handler answers anything as {@code /**}. */
    private static String controllerPatternOf(HttpServletRequest request) {
        if (request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod
                && request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) instanceof String pattern) {
            return pattern;
        }
        return null;
    }

    private static String unmatched(String uri) {
        String path = uri.startsWith("/") ? uri.substring(1) : uri;
        int slash = path.indexOf('/');
        String firstSegment = slash < 0 ? path : path.substring(0, slash);
        return "/" + firstSegment + "/<unmatched>";
    }

    private record StreamCompletion(HttpServletRequest request, Runnable writeLine) implements AsyncListener {

        @Override
        public void onComplete(AsyncEvent event) {
            writeLine.run();
        }

        @Override
        public void onError(AsyncEvent event) {
            if (ClientDisconnects.isDisconnect(event.getThrowable())) {
                ClientDisconnects.markGone(request);
            }
        }

        @Override
        public void onTimeout(AsyncEvent event) {
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
        }
    }
}
