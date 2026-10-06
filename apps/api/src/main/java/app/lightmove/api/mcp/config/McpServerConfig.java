package app.lightmove.api.mcp.config;

import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.McpSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.ratelimit.service.RateLimiter;
import app.lightmove.api.core.security.apikey.ApiKeyIntrospector;
import app.lightmove.api.core.security.apikey.ApiKeyPrincipal;
import app.lightmove.api.core.security.apikey.ApiKeyScope;
import app.lightmove.api.core.security.apikey.ApiKeySecrets;
import app.lightmove.api.core.security.apikey.ApiKeyThrottledException;
import app.lightmove.api.core.security.apikey.PublicApiProblemWriter;
import app.lightmove.api.core.security.oauth.McpServerIdentity;
import app.lightmove.api.core.security.oauth.McpTokenDecoder;
import app.lightmove.api.core.security.oauth.OAuthAuthorizationServerConfig;
import app.lightmove.api.core.security.service.ClientIpResolver;
import app.lightmove.api.mcp.model.McpCallOrigin;
import app.lightmove.api.mcp.model.McpCaller;
import app.lightmove.api.mcp.service.McpCredentials;
import app.lightmove.api.mcp.service.McpRateLimitFilter;
import app.lightmove.api.mcp.service.McpRequestLimitFilter;
import app.lightmove.api.mcp.service.McpScopeStepUpFilter;
import app.lightmove.api.mcp.service.McpToolGuard;
import app.lightmove.api.mcp.service.McpToolRegistry;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration.ServerMcpAnnotatedBeans;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * The MCP server at {@code /api/v1/mcp} (epic #699): Spring AI's stateless Streamable HTTP transport behind its own
 * resource-server chain. Two credentials open it, told apart by shape — an access token from our authorization server,
 * signed by the MCP key with the MCP endpoint as its audience, or an API key opted in with {@code mcp:use} — and a
 * session's token opens nothing, since it is neither. Tools call services in-process: no caller's token is ever passed on.
 */
@Configuration
@ConditionalOnBooleanProperty(name = OAuthAuthorizationServerConfig.MCP_SWITCH)
public class McpServerConfig {

    public static final String RESOURCE_METADATA = "/.well-known/oauth-protected-resource";

    /** Replaces Spring AI's own: the same transport, mounted under {@code /api/v1}, checking Origin, carrying the caller. */
    @Bean
    WebMvcStatelessServerTransport webMvcStatelessServerTransport(@Qualifier("mcpServerJsonMapper") JsonMapper json,
                                                                  LightMoveProperties properties,
                                                                  ClientIpResolver clientIps) {
        List<String> origins = properties.mcp().allowedOriginsUnder(properties.web().baseUrl());
        return WebMvcStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(json))
                .messageEndpoint(McpSettings.MCP_PATH)
                // A browser page on another site must not reach a server it can name (the spec's DNS-rebinding defence).
                .securityValidator(DefaultServerTransportSecurityValidator.builder().allowedOrigins(origins).build())
                .contextExtractor(request -> contextOf(request.servletRequest(), clientIps))
                .build();
    }

    @Bean
    McpToolRegistry mcpToolRegistry(ServerMcpAnnotatedBeans annotatedBeans) {
        return new McpToolRegistry(annotatedBeans.getBeansByAnnotation(McpTool.class));
    }

    /** In place of Spring AI's own list (excluded on the application): the same tools, each behind the guard. */
    @Bean
    List<SyncToolSpecification> mcpToolSpecifications(McpToolRegistry tools,
                                                      @Qualifier("mcpServerJsonMapper") JsonMapper json,
                                                      LightMoveProperties properties) {
        return tools.specificationsGuardedBy(new McpToolGuard(json, properties.mcp().maxResultChars()));
    }

    @Bean
    @Order(2)
    SecurityFilterChain mcpChain(HttpSecurity http, McpTokenDecoder mcpTokens, ApiKeyIntrospector keys,
                                 McpCredentials credentials, McpServerIdentity identity,
                                 PublicApiProblemWriter problems, LightMoveProperties properties, RateLimiter limiter,
                                 ClientIpResolver clientIps, AuditService audit, McpToolRegistry tools,
                                 @Qualifier("mcpServerJsonMapper") JsonMapper json,
                                 @Qualifier("corsConfigurationSource") CorsConfigurationSource cors) throws Exception {
        String metadataUrl = identity.issuer() + RESOURCE_METADATA + URI.create(identity.resourceUrl()).getRawPath();
        BearerTokenAuthenticationEntryPoint challenge = new BearerTokenAuthenticationEntryPoint();
        challenge.setResourceMetadataParameterResolver(request -> metadataUrl);
        AuthenticationEntryPoint refused = (request, response, failure) -> {
            if (failure instanceof ApiKeyThrottledException throttled) {
                response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(throttled.retryAfterSeconds()));
                problems.write(request, response, ErrorCode.RATE_LIMITED);
                return;
            }
            // An address is budgeted on its refusals alone: a guesser spends it, a hosted client's tenants never do.
            if (properties.auth().rateLimit().enabled() && !limiter.tryAcquire(
                    "mcp:refused:ip:" + clientIps.resolve(request), properties.mcp().refusalsPerMinutePerIp(),
                    McpRateLimitFilter.WINDOW)) {
                response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(McpRateLimitFilter.WINDOW.toSeconds()));
                problems.write(request, response, ErrorCode.RATE_LIMITED);
                return;
            }
            challenge.commence(request, response, failure);
            problems.write(request, response, ErrorCode.MCP_CREDENTIAL_INVALID);
        };

        return http
                .securityMatcher(McpSettings.MCP_PATH, McpSettings.MCP_PATH + "/**",
                        RESOURCE_METADATA, RESOURCE_METADATA + "/**")
                .csrf(csrf -> csrf.disable())
                // A browser client's preflight is answered here, before any credential is asked for.
                .cors(corsConfig -> corsConfig.configurationSource(cors))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, RESOURCE_METADATA, RESOURCE_METADATA + "/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(refused))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationManagerResolver(byCredentialShape(mcpTokens, keys, credentials))
                        .authenticationEntryPoint(refused)
                        .protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(builder ->
                                builder.resource(identity.resourceUrl())
                                        .resourceName("Uncava")
                                        .authorizationServer(identity.issuer())
                                        .scopes(scopes -> scopes.addAll(ApiKeyScope.dataScopes().stream()
                                                .map(ApiKeyScope::value).toList()))
                                        .claims(claims -> claims.remove("tls_client_certificate_bound_access_tokens")))))
                .addFilterBefore(new McpRequestLimitFilter(properties.mcp().maxRequestBytes(), problems),
                        BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new McpRateLimitFilter(properties.mcp(), properties.auth().rateLimit(), limiter,
                        problems, audit), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new McpScopeStepUpFilter(tools, json, metadataUrl, problems), McpRateLimitFilter.class)
                .build();
    }

    /** An API key is recognisable by its prefix and checksum; anything else is read as an MCP access token. */
    private static AuthenticationManagerResolver<HttpServletRequest> byCredentialShape(
            McpTokenDecoder mcpTokens, ApiKeyIntrospector keys, McpCredentials credentials) {
        JwtAuthenticationProvider tokenProvider = new JwtAuthenticationProvider(mcpTokens::decode);
        tokenProvider.setJwtAuthenticationConverter(credentials::fromAccessToken);
        AuthenticationManager tokens = new ProviderManager(tokenProvider);

        OpaqueTokenAuthenticationProvider keyProvider = new OpaqueTokenAuthenticationProvider(keys);
        keyProvider.setAuthenticationConverter((presented, principal) ->
                credentials.fromApiKey((ApiKeyPrincipal) principal));
        AuthenticationManager apiKeys = new ProviderManager(keyProvider);

        BearerTokenResolver bearer = new DefaultBearerTokenResolver();
        return request -> ApiKeySecrets.isWellFormed(bearer.resolve(request)) ? apiKeys : tokens;
    }

    private static McpTransportContext contextOf(HttpServletRequest request, ClientIpResolver clientIps) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> context = new HashMap<>();
        if (authentication != null && authentication.getPrincipal() instanceof McpCaller caller) {
            context.put(McpCaller.CONTEXT_KEY, caller);
        }
        context.put(McpCallOrigin.CONTEXT_KEY, new McpCallOrigin(clientIps.resolve(request),
                Objects.requireNonNullElse(request.getHeader(HttpHeaders.USER_AGENT), "")));
        return McpTransportContext.create(context);
    }
}
