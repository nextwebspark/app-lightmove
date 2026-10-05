package app.lightmove.api.core.logging.service;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The route template a request answered to, never its raw path: a path carries UUIDs, and every
 * distinct value would be its own series. Whatever no controller answers is one of a fixed few values.
 */
@Component
@Slf4j
public class RouteResolver {

    static final String UNMATCHED = "<unmatched>";

    /** The only roots {@link RequestLogFilter} lets through; anything else is one bucket, never the caller's text. */
    private static final List<String> KNOWN_ROOTS = List.of("api", "oauth2", "login");
    private static final String OTHER_ROOT = "<other>";

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMapping;

    public RouteResolver(
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    String resolve(HttpServletRequest request) {
        String dispatched = controllerPatternOf(request);
        if (dispatched != null) {
            return dispatched;
        }
        String refused = wasDispatched(request) ? null : patternOfRefused(request);
        return refused != null ? refused : unmatched(request.getRequestURI());
    }

    /**
     * A request the security chain refused (a 401, a 403) never reached the dispatcher, so it is looked up
     * here to keep its route. The lookup writes its attributes onto a throwaway wrapper, never the request.
     */
    private String patternOfRefused(HttpServletRequest request) {
        RequestMappingHandlerMapping mapping = handlerMapping.getIfAvailable();
        if (mapping == null || !request.getRequestURI().startsWith("/api/")) {
            return null;
        }
        AttributeIsolatingRequest probe = new AttributeIsolatingRequest(request);
        try {
            return mapping.getHandler(probe) != null ? controllerPatternOf(probe) : null;
        } catch (ServletException noController) {
            // The mapping's own answer for a wrong method, media type or parameter: no template to report.
            return null;
        } catch (Exception unexpected) {
            log.warn("Route lookup failed for a refused request", unexpected);
            return null;
        }
    }

    /** The dispatcher already looked this request up; a second walk would only repeat its miss. */
    private static boolean wasDispatched(HttpServletRequest request) {
        return request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) != null;
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
        String root = KNOWN_ROOTS.stream()
                .filter(known -> uri.startsWith("/" + known + "/"))
                .findFirst()
                .orElse(OTHER_ROOT);
        return "/" + root + "/" + UNMATCHED;
    }

    /** Reads through to the request; every write and removal stays on the wrapper. */
    private static final class AttributeIsolatingRequest extends HttpServletRequestWrapper {

        private final Map<String, Object> overlay = new HashMap<>();

        AttributeIsolatingRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public Object getAttribute(String name) {
            return overlay.containsKey(name) ? overlay.get(name) : super.getAttribute(name);
        }

        @Override
        public void setAttribute(String name, Object value) {
            overlay.put(name, value);
        }

        @Override
        public void removeAttribute(String name) {
            overlay.put(name, null);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            Set<String> names = new LinkedHashSet<>(Collections.list(super.getAttributeNames()));
            overlay.forEach((name, value) -> {
                if (value == null) {
                    names.remove(name);
                } else {
                    names.add(name);
                }
            });
            return Collections.enumeration(names);
        }
    }
}
