package app.lightmove.api.publicapi.config;

import app.lightmove.api.core.config.LightMoveProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** The public API's OpenAPI document and the short {@code /docs} address of its Swagger UI. */
@Configuration
public class PublicApiDocsConfig implements WebMvcConfigurer {

    private static final String KEY_SCHEME = "apiKey";

    private static final String DOCS = "/api/v1/public/docs";

    @Bean
    OpenAPI publicApiDocument(LightMoveProperties properties) {
        return new OpenAPI()
                .info(new Info()
                        .title("Uncava Public API")
                        .version("v1")
                        .description("""
                                Read-only access to a workspace's positions, their companies and their \
                                executives. Every request carries an API key made in Settings → API keys, \
                                as `Authorization: Bearer <key>`. A personal key reads what its owner can \
                                open; a workspace key reads every position. Each key carries scopes, and \
                                a route outside them answers 403. Errors are RFC 9457 problem documents \
                                whose `code` is the stable part."""))
                .servers(List.of(new Server().url(properties.web().baseUrl())))
                .components(new Components().addSecuritySchemes(KEY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("uncava_pat_… or uncava_svc_…")
                        .description("An API key from Settings → API keys")))
                .addSecurityItem(new SecurityRequirement().addList(KEY_SCHEME));
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController(DOCS, DOCS + "/swagger-ui/index.html");
    }
}
