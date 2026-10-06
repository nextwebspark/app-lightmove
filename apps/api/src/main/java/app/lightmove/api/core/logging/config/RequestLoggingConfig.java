package app.lightmove.api.core.logging.config;

import app.lightmove.api.core.logging.service.ProjectIdInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the interceptor that stamps a project route's id on its log lines. */
@Configuration
public class RequestLoggingConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ProjectIdInterceptor()).addPathPatterns("/api/v1/projects/**");
    }
}
