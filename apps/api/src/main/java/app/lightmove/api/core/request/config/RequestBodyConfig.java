package app.lightmove.api.core.request.config;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.WebSettings;
import app.lightmove.api.core.request.service.JsonBodySizeLimit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * How strictly a request body is read: unknown fields refused and an oversized body turned away. Applies to the
 * MVC converter alone — the shared {@link JsonMapper} also reads vendor responses and stays lenient.
 */
@Configuration
@RequiredArgsConstructor
public class RequestBodyConfig implements WebMvcConfigurer {

    private final LightMoveProperties properties;

    /** Ordered after Boot's own Jackson customizer, whose converter this one replaces. */
    @Bean
    @Order(1)
    ServerHttpMessageConvertersCustomizer strictRequestBodyConverter(JsonMapper jsonMapper) {
        return builder -> {
            if (properties.web().refuseUnknownRequestFields()) {
                JsonMapper strict = jsonMapper.rebuild()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build();
                builder.withJsonConverter(new JacksonJsonHttpMessageConverter(strict));
            }
        };
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        WebSettings web = properties.web();
        registry.addInterceptor(new JsonBodySizeLimit(web.maxJsonBodySize())).addPathPatterns("/api/v1/**");
    }
}
