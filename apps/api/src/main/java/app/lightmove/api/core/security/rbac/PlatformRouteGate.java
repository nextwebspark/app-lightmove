package app.lightmove.api.core.security.rbac;

import app.lightmove.api.core.security.model.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Answers 404 on every platform route to a caller holding no platform action, before the request body is read.
 * Each route's own {@code @platformAuthorizer} guard still decides the action and the factor; but it runs after
 * the body is bound and validated, so without this a workspace user sending a bad body was told 400 by a route
 * that must not admit to existing.
 */
@Configuration
@RequiredArgsConstructor
public class PlatformRouteGate implements WebMvcConfigurer, HandlerInterceptor {

    private final PlatformAccess access;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/v1/platform/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            access.requireAnyAction(principal.userId());
        }
        return true;
    }
}
