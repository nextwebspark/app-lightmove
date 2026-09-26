package app.lightmove.api.core.security.config;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.SpaRequestPaths;
import app.lightmove.api.core.error.handler.ProblemAccessDeniedHandler;
import app.lightmove.api.core.security.jwt.JwtPrincipalConverter;
import app.lightmove.api.core.security.service.CookieAuthorizationRequestStore;
import app.lightmove.api.core.security.service.OAuth2LoginFailureHandler;
import app.lightmove.api.core.security.service.OAuth2LoginSuccessHandler;
import app.lightmove.api.core.security.service.ProviderQuirkAwareRequestResolver;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
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

    /** Resolves {@code '{value}'} in {@code @RequireProjectPermission}; static, as method security reads it while being built. */
    @Bean
    static AnnotationTemplateExpressionDefaults annotationTemplateExpressionDefaults() {
        return new AnnotationTemplateExpressionDefaults();
    }

    /**
     * Chain 0: Actuator, matched on the port the request arrived on — a path match alone would open
     * {@code /actuator/prometheus} on the app port too. On Cloud Run the ports are equal and this chain
     * deliberately matches nothing; chain 3 then permits only health and info.
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
     * Chain 2: the SPA's assets and history fallback, matched by exclusion — so any endpoint outside
     * {@code /api/v1} is public; keep every endpoint under it ({@code SpaSecurityTest}).
     *
     * <p><b>Never {@code Cross-Origin-Opener-Policy: same-origin}</b>: it severs {@code window.opener}
     * and the OAuth popup silently hangs on "Connecting…"; {@code same-origin-allow-popups} is safe.
     */
    @Bean
    @Order(2)
    SecurityFilterChain spaChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(request -> SpaRequestPaths.isSpaPath(request.getRequestURI()))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /** Chain 3: everything else. Stateless bearer tokens, CSRF off (see the class note). */
    @Bean
    @Order(3)
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
                        .requestMatchers(API + "/onboarding/accept-invitation").access(verified)

                        // Nothing may exist on a firm's domain on the strength of an unopened address.
                        // Safe only because the wizard asks for the emailed link at step 2.
                        .requestMatchers(API + "/onboarding/**").access(verified)

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
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
