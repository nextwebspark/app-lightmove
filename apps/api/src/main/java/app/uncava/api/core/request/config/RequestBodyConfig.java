package app.uncava.api.core.request.config;

import app.uncava.api.core.config.UncavaProperties;
import app.uncava.api.core.request.service.JsonBodySizeFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * How strictly a request body is read: unknown fields refused and an oversized body turned away. Applies to the
 * MVC converter alone — the shared {@link JsonMapper} also reads vendor responses and stays lenient.
 */
@Configuration
@RequiredArgsConstructor
public class RequestBodyConfig {

    private final UncavaProperties properties;

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

    @Bean
    FilterRegistrationBean<JsonBodySizeFilter> jsonBodySizeFilter() {
        FilterRegistrationBean<JsonBodySizeFilter> registration =
                new FilterRegistrationBean<>(new JsonBodySizeFilter(properties.web().maxJsonBodySize()));
        registration.addUrlPatterns("/api/v1/*");
        return registration;
    }
}
