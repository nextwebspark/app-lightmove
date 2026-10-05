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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One INFO line per request, whatever its status (a 500's ERROR is {@code GlobalExceptionHandler}'s):
 * {@code method}, {@code route}, {@code status}, {@code durationMs}.
 * No field is named {@code httpRequest} — Cloud Logging would promote it into a second request entry
 * beside Cloud Run's. Runs inside {@link CorrelationIdFilter}, which clears the MDC only after this writes.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@Slf4j
@RequiredArgsConstructor
public class RequestLogFilter extends OncePerRequestFilter {

    /** nginx's convention for a client that closed the connection before the response. */
    static final int CLIENT_CLOSED_REQUEST = 499;

    private final RouteResolver routeResolver;

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
            String route = routeResolver.resolve(request);
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
