package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.ratelimit.service.RateLimiter;
import app.lightmove.api.core.security.apikey.ApiKeyScope;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.service.ClientIpResolver;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.PublicClientAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2ErrorAuthenticationFailureHandler;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tools.jackson.databind.json.JsonMapper;

/**
 * The OAuth 2.1 authorization server MCP clients connect through (epic #699): Spring's own, its endpoints moved under
 * {@code /api/v1/oauth} — {@code /oauth2/**} is the sign-in providers' — with the metadata at the RFC 8414 well-known
 * path. Public clients only, PKCE S256, a {@code resource} naming the MCP endpoint, a workspace chosen at consent, and
 * rotating refresh tokens.
 *
 * <p>The user is whoever the SPA's session token says, read by a bearer filter placed ahead of the framework's request
 * validation: that validation captures the principal, and the resource server's own filter would run too late for it.
 * No cookie is read on this chain, so it needs no CSRF protection.
 */
@Configuration
@ConditionalOnBooleanProperty(name = OAuthAuthorizationServerConfig.MCP_SWITCH)
public class OAuthAuthorizationServerConfig {

    public static final String MCP_SWITCH = "lightmove.mcp.enabled";

    public static final String OAUTH_BASE = "/api/v1/oauth";
    public static final String AUTHORIZE = OAUTH_BASE + "/authorize";
    public static final String TOKEN = OAUTH_BASE + "/token";
    public static final String JWKS = OAUTH_BASE + "/jwks";
    public static final String REVOKE = OAUTH_BASE + "/revoke";
    public static final String REGISTER = OAUTH_BASE + "/register";
    public static final String CONSENT = OAUTH_BASE + "/consent";
    public static final String METADATA = "/.well-known/oauth-authorization-server";

    @Bean
    AuthorizationServerSettings authorizationServerSettings(McpServerIdentity identity) {
        return AuthorizationServerSettings.builder()
                .issuer(identity.issuer())
                .authorizationEndpoint(AUTHORIZE)
                .tokenEndpoint(TOKEN)
                .jwkSetEndpoint(JWKS)
                .tokenRevocationEndpoint(REVOKE)
                .tokenIntrospectionEndpoint(OAUTH_BASE + "/introspect")
                .pushedAuthorizationRequestEndpoint(OAUTH_BASE + "/par")
                .deviceAuthorizationEndpoint(OAUTH_BASE + "/device_authorization")
                .deviceVerificationEndpoint(OAUTH_BASE + "/device_verification")
                .clientRegistrationEndpoint(REGISTER)
                .oidcClientRegistrationEndpoint(OAUTH_BASE + "/connect/register")
                .oidcUserInfoEndpoint(OAUTH_BASE + "/userinfo")
                .oidcLogoutEndpoint(OAUTH_BASE + "/connect/logout")
                .build();
    }

    /** Disjoint from the cookie chain that shares its order, and ahead of the SPA's and the general one. */
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerChain(HttpSecurity http, McpServerIdentity identity,
                                                 JWKSource<SecurityContext> mcpJwkSource,
                                                 RegisteredClientRepository clients,
                                                 ClientMetadataDocumentClients documentClients,
                                                 HashingAuthorizationService authorizations,
                                                 PerGrantConsentService consents,
                                                 AuthorizationServerSettings settings,
                                                 JwtDecoder sessionDecoder,
                                                 WorkspaceAccess access,
                                                 JsonMapper json,
                                                 AuditService audit,
                                                 RateLimiter limiter,
                                                 ClientIpResolver clientIps,
                                                 LightMoveProperties properties,
                                                 Clock clock) throws Exception {
        McpAuthorizationRules rules = new McpAuthorizationRules(identity, access, authorizations);
        AuthorizationEndpointReplies replies = new AuthorizationEndpointReplies(identity, json);

        JwtGenerator accessTokens = new JwtGenerator(new NimbusJwtEncoder(mcpJwkSource));
        accessTokens.setJwtCustomizer(new McpAccessTokenCustomizer(identity));

        http
                .oauth2AuthorizationServer(server -> configure(http, server)
                        .registeredClientRepository(clients)
                        .authorizationService(authorizations)
                        .authorizationConsentService(consents)
                        .authorizationServerSettings(settings)
                        .tokenGenerator(new DelegatingOAuth2TokenGenerator(
                                accessTokens, new RotatingRefreshTokenGenerator(clock)))
                        .clientAuthentication(clientAuthentication -> clientAuthentication
                                .authenticationConverter(new PublicClientAuthentication.Converter(TOKEN, REVOKE))
                                .authenticationProvider(new PublicClientAuthentication.Provider(documentClients))
                                .authenticationProviders(providers -> providers.replaceAll(provider ->
                                        provider instanceof PublicClientAuthenticationProvider framework
                                                ? new PublicClientAuthentication.CodeGrantOnly(framework)
                                                : provider)))
                        .clientRegistrationEndpoint(registration -> registration
                                .openRegistrationAllowed(true)
                                .authenticationProviders(providers -> providers.forEach(provider -> {
                                    if (provider instanceof OAuth2ClientRegistrationAuthenticationProvider register) {
                                        register.setAuthenticationValidator(DynamicClientRegistrations::validate);
                                        register.setRegisteredClientConverter(registered ->
                                                DynamicClientRegistrations.toRegisteredClient(registered, clock));
                                    }
                                })))
                        .tokenRevocationEndpoint(revocation -> revocation
                                .authenticationProviders(providers -> {
                                    providers.clear();
                                    providers.add(new PublicClientRevocation(authorizations));
                                }))
                        .authorizationEndpoint(authorize -> authorize
                                // Absolute, on the deployment's origin: the consent screen's fetch follows
                                // this redirect with its bearer, which a browser drops on a hop to another
                                // origin — the API's own host behind a proxy rewriting Host, as dev's does.
                                .consentPage(identity.issuer() + CONSENT)
                                .authorizationResponseHandler(replies)
                                .errorResponseHandler(replies)
                                .authenticationProviders(providers -> providers.forEach(provider -> {
                                    if (provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider request) {
                                        request.setAuthenticationValidator(rules.requestValidator());
                                        // The workspace is chosen per grant, so every grant is asked for.
                                        request.setAuthorizationConsentRequired(context -> true);
                                    }
                                    if (provider instanceof OAuth2AuthorizationConsentAuthenticationProvider consent) {
                                        consent.setAuthorizationConsentCustomizer(rules::validateConsent);
                                    }
                                })))
                        .tokenEndpoint(token -> token
                                .accessTokenRequestConverters(converters ->
                                        ResourceBoundTokenRequests.requireResource(converters, identity))
                                .errorResponseHandler(auditingRefreshFailures(audit)))
                        .authorizationServerMetadataEndpoint(metadata -> metadata
                                .authorizationServerMetadataCustomizer(builder -> builder
                                        .grantTypes(types -> {
                                            types.clear();
                                            types.add(AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
                                            types.add(AuthorizationGrantType.REFRESH_TOKEN.getValue());
                                        })
                                        .tokenEndpointAuthenticationMethods(methods -> {
                                            methods.clear();
                                            methods.add(ClientAuthenticationMethod.NONE.getValue());
                                        })
                                        .codeChallengeMethods(methods -> {
                                            methods.clear();
                                            methods.add("S256");
                                        })
                                        .scopes(scopes -> scopes.addAll(
                                                ApiKeyScope.dataScopes().stream().map(ApiKeyScope::value).toList()))
                                        .tokenRevocationEndpointAuthenticationMethods(methods -> {
                                            methods.clear();
                                            methods.add(ClientAuthenticationMethod.NONE.getValue());
                                        })
                                        .claim("authorization_response_iss_parameter_supported", true)
                                        .claim("client_id_metadata_document_supported", true)
                                        .claims(claims -> List.of("device_authorization_endpoint",
                                                        "introspection_endpoint",
                                                        "introspection_endpoint_auth_methods_supported",
                                                        "tls_client_certificate_bound_access_tokens",
                                                        "dpop_signing_alg_values_supported")
                                                .forEach(claims::remove)))))
                .authorizeHttpRequests(authorize -> authorize
                        // Registration needs no one signed in: an MCP client registers before anyone has.
                        .requestMatchers(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, REGISTER))
                        .permitAll()
                        .anyRequest().authenticated())
                // Turning registration on makes the framework add a resource server for initial access tokens; it is
                // pinned to the session decoder and principal here, so it reads the bearer exactly as ours does.
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt
                        .decoder(sessionDecoder)
                        .jwtAuthenticationConverter(SessionUserAuthentication::of)))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(replies,
                        PathPatternRequestMatcher.withDefaults().matcher(AUTHORIZE)))
                .addFilterBefore(new OAuthRateLimitFilter(AUTHORIZE, TOKEN, REGISTER, REVOKE, properties.mcp(),
                        properties.auth().rateLimit(), limiter, clientIps, audit), CsrfFilter.class)
                .addFilterBefore(sessionBearerFilter(sessionDecoder), X509AuthenticationFilter.class);

        return http.build();
    }

    private static OAuth2AuthorizationServerConfigurer configure(HttpSecurity http,
                                                                 OAuth2AuthorizationServerConfigurer server) {
        http.securityMatcher(server.getEndpointsMatcher());
        return server;
    }

    /**
     * The SPA's session token, read here rather than by {@code oauth2ResourceServer}: the framework's request
     * validation runs ahead of that filter and keeps whatever principal it found, so a later one never reaches it.
     */
    private static BearerTokenAuthenticationFilter sessionBearerFilter(JwtDecoder sessionDecoder) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(sessionDecoder);
        provider.setJwtAuthenticationConverter(SessionUserAuthentication::of);
        BearerTokenAuthenticationFilter filter = new BearerTokenAuthenticationFilter(new ProviderManager(provider));
        filter.setAuthenticationFailureHandler(
                new AuthenticationEntryPointFailureHandler(new BearerTokenAuthenticationEntryPoint()));
        return filter;
    }

    /** Every refused refresh is an audit line: a burst of them is a stolen token being tried. */
    private static AuthenticationFailureHandler auditingRefreshFailures(AuditService audit) {
        OAuth2ErrorAuthenticationFailureHandler standard = new OAuth2ErrorAuthenticationFailureHandler();
        return (request, response, exception) -> {
            if (AuthorizationGrantType.REFRESH_TOKEN.getValue()
                    .equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))) {
                AuditService.Builder event = audit.event(WorkspaceEventType.OAUTH_TOKEN_REFRESHED).failed()
                        .from(request)
                        .detailIfPresent("clientId", request.getParameter(OAuth2ParameterNames.CLIENT_ID));
                if (exception instanceof OAuth2AuthenticationException oauth) {
                    event.reason(oauth.getError().getErrorCode());
                }
                event.record();
            }
            standard.onAuthenticationFailure(request, response, exception);
        };
    }
}
