package app.lightmove.api.core.security.config;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.McpSettings;
import app.lightmove.api.core.config.SpaRequestPaths;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.handler.ProblemAccessDeniedHandler;
import app.lightmove.api.core.security.apikey.ApiKeyIntrospector;
import app.lightmove.api.core.security.apikey.ApiKeyThrottledException;
import app.lightmove.api.core.security.apikey.PublicApiProblemWriter;
import app.lightmove.api.core.security.jwt.JwtPrincipalConverter;
import app.lightmove.api.core.security.oauth.OAuthAuthorizationServerConfig;
import app.lightmove.api.core.security.service.CookieAuthorizationRequestStore;
import app.lightmove.api.core.security.service.OAuth2LoginFailureHandler;
import app.lightmove.api.core.security.service.OAuth2LoginSuccessHandler;
import app.lightmove.api.core.security.service.ProviderQuirkAwareRequestResolver;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The filter chains. Bearer-token routes need no CSRF protection — a browser never attaches the header
 * on its own — while the cookie routes ({@code /auth/refresh}, {@code /auth/logout}) keep it on, since a
 * cross-site request carries the cookie automatically. Never disable CSRF across the board.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String API = "/api/v1";
    private static final String PUBLIC_API = API + "/public";
    private static final String PUBLIC_API_SWITCH = "lightmove.public-api.enabled";

    /** Swagger UI is served unauthenticated on the SPA's origin, so it may load and call nothing but this origin. */
    private static final String PUBLIC_API_CSP = "default-src 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; "
            + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    /** Framing alone: the bundle's own sources (Mapbox, Nylas, the fonts) are not pinned here. */
    private static final String SPA_CSP = "frame-ancestors 'none'";

    /** Resolves {@code '{value}'} in {@code @RequireProjectPermission}; static, as method security reads it while being built. */
    @Bean
    static AnnotationTemplateExpressionDefaults annotationTemplateExpressionDefaults() {
        return new AnnotationTemplateExpressionDefaults();
    }

    /**
     * Chain 0: Actuator, matched on the port the request arrived on — a path match alone would open
     * {@code /actuator/prometheus} on the app port too. On Cloud Run the ports are equal and this chain
     * deliberately matches nothing; chain 4 then permits only health and info.
     */
    @Bean
    @Order(0)
    SecurityFilterChain actuatorChain(HttpSecurity http, ServerProperties server,
                                      ObjectProvider<ManagementServerProperties> management) throws Exception {
        // ObjectProvider: the bean exists only when the management port differs, so a plain parameter
        // fails to inject in the same-port case Cloud Run forces and the application does not start.
        ManagementServerProperties properties = management.getIfAvailable();
        Integer managementPort = properties == null ? null : properties.getPort();
        int appPort = server.getPort() == null ? 8080 : server.getPort();

        if (managementPort == null || managementPort.equals(appPort)) {
            return http.securityMatcher(request -> false).build();
        }

        return http
                .securityMatcher(request -> request.getLocalPort() == managementPort)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /** Chain 1: the cookie-authenticated auth endpoints, CSRF on; ordered before the general chain. */
    @Bean
    @Order(1)
    SecurityFilterChain cookieAuthChain(HttpSecurity http,
                                        @Qualifier("corsConfigurationSource") CorsConfigurationSource cors,
                                        JwtPrincipalConverter principalConverter,
                                        ProblemAccessDeniedHandler accessDenied)
            throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();

        return http
                .securityMatcher(API + "/auth/**")
                .cors(c -> c.configurationSource(cors))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // A CSRF refusal happens in the filter chain, where no @RestControllerAdvice sees it;
                // without this it is a bodiless 403 the SPA cannot tell from a real denial.
                .exceptionHandling(e -> e.accessDeniedHandler(accessDenied))

                // Double-submit: the XSRF-TOKEN cookie is readable by design; another origin can cause
                // it to be sent but cannot read it to produce the matching header.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // Entry points with no cookie to protect; they must work before any token exists.
                        .ignoringRequestMatchers(
                                API + "/auth/signup",
                                API + "/auth/login",
                                API + "/auth/verify",
                                API + "/auth/verify/resend",
                                API + "/auth/password/forgot",
                                API + "/auth/password/reset",
                                // The extension's refresh token travels in the body, not a cookie.
                                // Deliberately not /auth/extension/**: minting a token stays protected.
                                API + "/auth/extension/refresh",
                                API + "/auth/extension/logout"))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                API + "/auth/signup",
                                API + "/auth/login",
                                API + "/auth/refresh",   // authenticated by the cookie, not a bearer token
                                API + "/auth/logout",
                                API + "/auth/verify",
                                API + "/auth/verify/resend",
                                API + "/auth/password/forgot",
                                API + "/auth/password/reset",
                                API + "/auth/csrf",
                                API + "/auth/providers",
                                API + "/auth/extension/refresh",
                                API + "/auth/extension/logout")
                        .permitAll()
                        // Including /auth/extension/tokens, which must only mint for the caller's own account.
                        .anyRequest().authenticated())

                // Must be the main chain's converter: with Spring's default the principal is a raw Jwt,
                // the AuthPrincipal parameter resolves to null, and /auth/me NPEs on a valid token.
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(principalConverter)))
                .build();
    }

    /**
     * Chain 2: the public API, where an API key is the only credential — a session's token is refused here
     * as a key is everywhere else. The spec and Swagger UI need none.
     */
    @Bean
    @Order(2)
    @ConditionalOnBooleanProperty(name = PUBLIC_API_SWITCH, matchIfMissing = true)
    SecurityFilterChain publicApiChain(HttpSecurity http, ApiKeyIntrospector introspector,
                                       PublicApiProblemWriter problems) throws Exception {
        BearerTokenAuthenticationEntryPoint bearerChallenge = new BearerTokenAuthenticationEntryPoint();
        AuthenticationEntryPoint keyRefused = (request, response, failure) -> {
            if (failure instanceof ApiKeyThrottledException throttled) {
                response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(throttled.retryAfterSeconds()));
                problems.write(request, response, ErrorCode.RATE_LIMITED);
                return;
            }
            bearerChallenge.commence(request, response, failure);
            problems.write(request, response, ErrorCode.API_KEY_INVALID);
        };

        return publicApi(http)
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(PUBLIC_API_CSP)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET,
                                PUBLIC_API + "/openapi.json", PUBLIC_API + "/openapi.json/**",
                                PUBLIC_API + "/docs", PUBLIC_API + "/docs/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(keyRefused))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(keyRefused)
                        .opaqueToken(opaque -> opaque.introspector(introspector)))
                .build();
    }

    /** Chain 2 with the public API switched off: everything under it, the docs included, is a 404. */
    @Bean
    @Order(2)
    @ConditionalOnBooleanProperty(name = PUBLIC_API_SWITCH, havingValue = false)
    SecurityFilterChain publicApiOffChain(HttpSecurity http, PublicApiProblemWriter problems) throws Exception {
        return publicApi(http)
                .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, failure) ->
                                problems.write(request, response, ErrorCode.NOT_FOUND))
                        .accessDeniedHandler((request, response, denial) ->
                                problems.write(request, response, ErrorCode.NOT_FOUND)))
                .build();
    }

    /** MCP switched off (the authorization server and the MCP server are both absent): all of it a 404. */
    @Bean
    @Order(1)
    @ConditionalOnBooleanProperty(name = OAuthAuthorizationServerConfig.MCP_SWITCH, havingValue = false,
            matchIfMissing = true)
    SecurityFilterChain mcpOffChain(HttpSecurity http, PublicApiProblemWriter problems) throws Exception {
        return http
                .securityMatcher(OAuthAuthorizationServerConfig.OAUTH_BASE + "/**",
                        McpSettings.MCP_PATH, McpSettings.MCP_PATH + "/**",
                        "/.well-known/oauth-*", "/.well-known/oauth-*/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, failure) ->
                                problems.write(request, response, ErrorCode.NOT_FOUND))
                        .accessDeniedHandler((request, response, denial) ->
                                problems.write(request, response, ErrorCode.NOT_FOUND)))
                .build();
    }

    private static HttpSecurity publicApi(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(PUBLIC_API + "/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }

    /**
     * Chain 3: the SPA's assets and history fallback, matched by exclusion — so any endpoint outside
     * {@code /api/v1} is public; keep every endpoint under it ({@code SpaSecurityTest}).
     *
     * <p><b>Never {@code Cross-Origin-Opener-Policy: same-origin}</b>: it severs {@code window.opener}
     * and the OAuth popup silently hangs on "Connecting…"; {@code same-origin-allow-popups} is safe.
     */
    @Bean
    @Order(3)
    SecurityFilterChain spaChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(request -> SpaRequestPaths.isSpaPath(request.getRequestURI()))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // No page of the app is ever framed, and the MCP consent screen's Allow must not be clickjacked.
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(SPA_CSP)))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /** Chain 4: everything else. Stateless bearer tokens, CSRF off (see the class note). */
    @Bean
    @Order(4)
    SecurityFilterChain apiChain(HttpSecurity http,
                                 // HandlerMappingIntrospector is also a CorsConfigurationSource.
                                 @Qualifier("corsConfigurationSource") CorsConfigurationSource cors,
                                 JwtPrincipalConverter principalConverter,
                                 OAuth2LoginSuccessHandler oauthSuccessHandler,
                                 OAuth2LoginFailureHandler oauthFailureHandler,
                                 CookieAuthorizationRequestStore authorizationRequestStore,
                                 ObjectProvider<ClientRegistrationRepository> clientRegistrations,
                                 ProblemAccessDeniedHandler accessDenied,
                                 LightMoveProperties properties) throws Exception {
        AuthorizationManager<RequestAuthorizationContext> verified =
                verifiedEmail(properties.auth().requireVerifiedEmail());

        http
                .cors(c -> c.configurationSource(cors))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                        // Every verified-email refusal lands here; the default writes an empty 403.
                        .accessDeniedHandler(accessDenied))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Liveness only. A workspace role must never double as a system role: this was
                        // once hasRole("ADMIN"), and any customer could scrape our metrics.
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").denyAll()

                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()

                        // Anonymous: the 256-bit token, mailed to the address the preview names, is the credential.
                        .requestMatchers(HttpMethod.GET, API + "/onboarding/invitations/preview").permitAll()

                        // Public: the mailed token is the mailbox proof, and the account is bound to the
                        // invited address, never a client-supplied one. POST-only, never a navigation.
                        .requestMatchers(HttpMethod.POST, API + "/onboarding/accept-invitation-signup").permitAll()

                        // Verified-only: accepting lands you ACTIVE with real candidate data at once, and a
                        // forwarded link is not proof of the mailbox. The token-less variant relies on the
                        // verified matching address as that proof.
                        .requestMatchers(API + "/onboarding/invitations/accept").access(verified)
                        .requestMatchers(API + "/onboarding/invitations/*/accept").access(verified)

                        // Nothing may exist on a firm's domain on the strength of an unopened address.
                        // Safe only because the wizard asks for the emailed link at step 2.
                        .requestMatchers(API + "/onboarding/**").access(verified)

                        // A navigation back from a mailbox provider's or Zoom's consent screen, so no bearer token:
                        // the single-use state, bound to the starting browser by a cookie, is the credential.
                        .requestMatchers(HttpMethod.GET, API + "/outreach/mailbox/callback").permitAll()
                        .requestMatchers(HttpMethod.GET, API + "/outreach/zoom/callback").permitAll()
                        // The booking link's page: an executive opens it from an email, holding no session.
                        .requestMatchers(HttpMethod.GET, API + "/outreach/booking/*", API + "/outreach/booking/*/slots")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, API + "/outreach/booking/*").permitAll()

                        // The mail service's webhook holds no bearer token: the delivery's HMAC signature,
                        // checked by OutreachInboxService before anything is read, is its credential.
                        .requestMatchers(API + "/outreach/webhooks/mailbox").permitAll()
                        // Recall's likewise: its Svix signature, checked by RecallCalendarClient, is the credential.
                        .requestMatchers(HttpMethod.POST, API + "/outreach/webhooks/recall").permitAll()

                        // Tenant data: an unverified user may not read a single candidate record.
                        .requestMatchers(API + "/**").access(verified)

                        .anyRequest().authenticated())

                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(principalConverter)));

        // Only when a provider is configured, or a fresh clone cannot start. The failure handler is
        // required: Spring's default redirects to /login?error on the API host, a 404.
        ClientRegistrationRepository registrations = clientRegistrations.getIfAvailable();
        if (registrations != null) {
            // Built here, not as a bean: the repository is auto-configured after user config, so a
            // @ConditionalOnBean on it silently never matches and the default resolver is used.
            var authorizationRequests = new ProviderQuirkAwareRequestResolver(
                    registrations,
                    properties.auth().oauth().pkceUnsupportedRegistrations(),
                    properties.auth().oauth().nonceUnsupportedRegistrations());

            http.oauth2Login(login -> login
                    .authorizationEndpoint(endpoint -> endpoint
                            .authorizationRequestResolver(authorizationRequests)
                            .authorizationRequestRepository(authorizationRequestStore))
                    .successHandler(oauthSuccessHandler)
                    .failureHandler(oauthFailureHandler));
        }

        return http.build();
    }

    /**
     * Authenticated and, unless the deployment opted out, holding a verified email. Guards onboarding
     * writes too: otherwise anyone could sign up as {@code victim@realfirm.com} and become ADMIN of a
     * workspace on a domain that isn't theirs.
     */
    private static AuthorizationManager<RequestAuthorizationContext> verifiedEmail(boolean required) {
        return (authentication, context) -> {
            var auth = authentication.get();
            boolean ok = auth != null && auth.isAuthenticated()
                    && (!required || auth.getAuthorities().stream()
                    .anyMatch(a -> JwtPrincipalConverter.VERIFIED_AUTHORITY.equals(a.getAuthority())));
            return new AuthorizationDecision(ok);
        };
    }

    /** The MCP endpoint's browser origins: the deployment's own and those listed, as its transport also checks. */
    private static CorsConfiguration mcpCors(LightMoveProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.mcp().allowedOriginsUnder(properties.web().baseUrl()));
        config.setAllowedMethods(List.of("POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Mcp-Protocol-Version"));
        config.setExposedHeaders(List.of("WWW-Authenticate", "Retry-After"));
        config.setAllowCredentials(false);
        return config;
    }

    /** {@code allowCredentials} is why the origin list must be explicit: never a wildcard. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(LightMoveProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.web().corsAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN", "X-Correlation-Id"));
        config.setExposedHeaders(List.of("X-Correlation-Id"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Registered first, so it wins for its path: MCP clients carry a bearer, never a cookie, so no credentials.
        source.registerCorsConfiguration(McpSettings.MCP_PATH, mcpCors(properties));
        source.registerCorsConfiguration(McpSettings.MCP_PATH + "/**", mcpCors(properties));
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
