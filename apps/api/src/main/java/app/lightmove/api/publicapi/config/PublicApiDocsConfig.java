package app.lightmove.api.publicapi.config;

import app.lightmove.api.core.config.CompanyListSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PublicApiSettings;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** The public API's OpenAPI document and the short {@code /docs} address of its Swagger UI. */
@Configuration
public class PublicApiDocsConfig implements WebMvcConfigurer {

    private static final String KEY_SCHEME = "apiKey";

    private static final String DOCS = "/api/v1/public/docs";

    private static final String SCHEMA_REF = "#/components/schemas/";

    /** springdoc names {@code PublicPage<Company>} {@code PublicPageCompany}; the public name is {@code CompanyPage}. */
    private static final Pattern GENERIC_PAGE = Pattern.compile("PublicPage(\\w+)");

    @Bean
    OpenAPI publicApiDocument(LightMoveProperties properties) {
        return new OpenAPI()
                .info(new Info()
                        .title("Uncava Public API")
                        .version("v1")
                        .contact(new Contact().name("Uncava").url(properties.web().baseUrl()))
                        .description(description(properties.publicApi(), properties.company().list())))
                .servers(List.of(new Server().url(properties.web().baseUrl())))
                .components(new Components().addSecuritySchemes(KEY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("uncava_pat_… or uncava_svc_…")
                        .description("An API key from Settings → API keys")))
                .addSecurityItem(new SecurityRequirement().addList(KEY_SCHEME));
    }

    @Bean
    OpenApiCustomizer publicSchemaNames() {
        return openApi -> {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            if (schemas == null) {
                return;
            }
            Map<String, String> renamed = new LinkedHashMap<>();
            Map<String, Schema> named = new LinkedHashMap<>();
            schemas.forEach((name, schema) -> {
                Matcher page = GENERIC_PAGE.matcher(name);
                String publicName = page.matches() ? page.group(1) + "Page" : name;
                renamed.put(name, publicName);
                named.put(publicName, schema);
            });
            openApi.getComponents().setSchemas(named);
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                    operation.getResponses().values().forEach(response -> {
                        if (response.getContent() != null) {
                            response.getContent().values().forEach(media -> {
                                Schema<?> schema = media.getSchema();
                                if (schema != null && schema.get$ref() != null) {
                                    String old = schema.get$ref().substring(SCHEMA_REF.length());
                                    schema.set$ref(SCHEMA_REF + renamed.getOrDefault(old, old));
                                }
                            });
                        }
                    })));
        };
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController(DOCS, DOCS + "/swagger-ui/index.html");
    }

    private static String description(PublicApiSettings limits, CompanyListSettings paging) {
        return """
                Read-only access to a workspace's positions, their companies and their executives, as JSON.

                ## Authentication
                Make a key in **Settings → API keys** and send it on every request as \
                `Authorization: Bearer <key>`. The key is shown once, when it is made; Uncava keeps only a \
                fingerprint of it.

                | Key | Prefix | Reads |
                |---|---|---|
                | Personal | `uncava_pat_` | Only the positions its owner can open in Uncava, re-checked on every \
                request. It stops working when its owner leaves the workspace. |
                | Workspace | `uncava_svc_` | Every position in the workspace. Only an admin makes one. |

                Every key expires; a revoked, expired or unknown key answers `401 API_KEY_INVALID`. No key \
                writes anything.

                ## Scopes
                A key carries the scopes chosen when it was made. A route outside them answers \
                `403 API_KEY_SCOPE_MISSING`, naming the scope in `requiredScope`.

                | Scope | Reads |
                |---|---|
                | `projects:read` | Positions: title, client, stage, type, dates and counts |
                | `companies:read` | Each position's companies, a stage at a time |
                | `candidates:read` | Each position's executives: profile, company and status |
                | `candidates.contacts:read` | Fills an executive's `contacts`; null without it |
                | `candidates.compensation:read` | Fills an executive's `compensation`; null without it |

                An executive's seniority, nationality, gender and years of experience are null until a person \
                has recorded or confirmed them. Notes and AI assessments are never sent.

                ## Paging
                List routes take `page` (from 0) and `size` (default %d, at most %d) and answer \
                `{ data, page, size, totalCount }`. The universe route answers a whole stage in one call, or \
                refuses one too large for it.

                ## Rate limits
                %d requests a minute per key, and %d per IP address. Past either, the answer is \
                `429 RATE_LIMITED` with a `Retry-After` header in seconds.

                ## Errors
                Every error is an RFC 9457 problem document (`application/problem+json`). Switch on `code`, \
                never on `detail`; quote `correlationId` when asking for support.

                | Status | Code | When |
                |---|---|---|
                | 400 | `VALIDATION_FAILED` | A parameter is out of range or not one of its values |
                | 400 | `PUBLIC_API_UNIVERSE_TOO_LARGE` | A universe too large for one call; page instead |
                | 401 | `API_KEY_INVALID` | The key is missing, malformed, unknown, revoked or expired |
                | 403 | `API_KEY_SCOPE_MISSING` | The key lacks the scope the route needs |
                | 403 | `FORBIDDEN` | A personal key's owner is not on the position |
                | 404 | `NOT_FOUND` | No such position in the key's workspace |
                | 429 | `RATE_LIMITED` | Too many requests; wait for `Retry-After` |
                """.formatted(paging.defaultPageSize(), paging.maxPageSize(), limits.requestsPerMinute(),
                limits.requestsPerMinutePerIp());
    }
}
