package app.lightmove.api.core.logging.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Puts a project route's {@code {projectId}} in the MDC. An interceptor rather than a filter because
 * the path variables exist only once a handler has matched, and setting it before the controller runs
 * puts it on every line the request writes, not only the closing one.
 *
 * <p>Only a well-formed UUID is put: the path is the caller's, and a junk segment has no place in the
 * JSON. Clears nothing; {@link CorrelationIdFilter} owns the MDC.
 */
public class ProjectIdInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables
                && variables.get("projectId") instanceof String projectId) {
            try {
                MDC.put(CorrelationId.PROJECT_ID_KEY, UUID.fromString(projectId).toString());
            } catch (IllegalArgumentException notAUuid) {
                // The controller refuses it; the log line simply goes without.
            }
        }
        return true;
    }
}
