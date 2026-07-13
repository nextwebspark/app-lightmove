package app.lightmove.api.auth.infrastructure;

import app.lightmove.api.common.config.LightMoveProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * The filter chains.
 *
 * <p>There are two, and splitting them is the point — because LightMove authenticates in two different
 * ways, with two different threat models:
 *
 * <ul>
 *   <li><b>Bearer-token routes</b> (almost everything). The caller proves themselves with an
 *       {@code Authorization: Bearer} header that JavaScript had to attach deliberately. A browser
 *       never attaches it on its own, so a cross-site request simply arrives unauthenticated —
 *       CSRF is structurally impossible, and CSRF tokens on these routes would be pure ceremony.
 *   <li><b>Cookie routes</b> ({@code /auth/refresh}, {@code /auth/logout}). The caller proves
 *       themselves with a cookie, which the browser attaches <i>automatically</i>, including on a
 *       request that evil.com made. That is textbook CSRF, and it is why these routes keep CSRF
 *       protection on.
 * </ul>
 *
 * <p>Spring Security 7 enables CSRF for API endpoints by default — a change from Spring Security 6,
 * and the single most common reason a Boot 3 auth tutorial fails on Boot 4. The lazy fix is
 * {@code csrf(AbstractHttpConfigurer::disable)} across the board. That would also switch it off for
 * the two routes that genuinely need it, which is exactly backwards.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String API = "/api/v1";

    /**
     * Chain 1: the cookie-authenticated auth endpoints. CSRF stays ON.
     *
     * <p>Ordered first so it claims {@code /api/v1/auth/**} before the general chain sees it.
     */
    @Bean
    @Order(1)
    SecurityFilterChain cookieAuthChain(HttpSecurity http,
                                        @Qualifier("corsConfigurationSource") CorsConfigurationSource cors,
                                        JwtPrincipalConverter principalConverter)
            throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();

        return http
                .securityMatcher(API + "/auth/**")
                .cors(c -> c.configurationSource(cors))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Double-submit: the SPA reads the XSRF-TOKEN cookie (readable by design — that is
                // what withHttpOnlyFalse means) and echoes it in the X-XSRF-TOKEN header. Another
                // origin can cause the cookie to be *sent*, but the same-origin policy stops it being
                // *read*, so it cannot produce the matching header.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // Entry points, not state changes: these have no cookie to protect and must
                        // work on a first visit, before any CSRF token exists.
                        .ignoringRequestMatchers(
                                API + "/auth/signup",
                                API + "/auth/login",
                                API + "/auth/verify",
                                API + "/auth/verify/resend"))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                API + "/auth/signup",
                                API + "/auth/login",
                                API + "/auth/refresh",   // authenticated by the cookie, not a bearer token
                                API + "/auth/logout",
                                API + "/auth/verify",
                                API + "/auth/verify/resend",
                                API + "/auth/csrf",
                                API + "/auth/providers")
                        .permitAll()
                        .anyRequest().authenticated())

                // The same converter as the main chain, and it must be. Not every route here is
                // anonymous — /auth/me is bearer-authenticated and reads the AuthPrincipal. With
                // Spring's default converter the principal is a raw Jwt, CurrentUser finds no
                // AuthPrincipal, and /auth/me answers 401 to a caller holding a perfectly valid token.
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(principalConverter)))
                .build();
    }

    /**
     * Chain 2: everything else. Stateless bearer tokens, CSRF off (see the class note).
     */
    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http,
                                 // Qualified because Spring MVC's HandlerMappingIntrospector is also a
                                 // CorsConfigurationSource, so the type alone is ambiguous.
                                 @Qualifier("corsConfigurationSource") CorsConfigurationSource cors,
                                 JwtPrincipalConverter principalConverter,
                                 OAuth2LoginSuccessHandler oauthSuccessHandler,
                                 ObjectProvider<ClientRegistrationRepository> clientRegistrations,
                                 LightMoveProperties properties) throws Exception {
        boolean requireVerified = properties.auth().requireVerifiedEmail();

        http
                .cors(c -> c.configurationSource(cors))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint()))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")

                        // Google sign-in. Spring owns these paths.
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()

                        // Onboarding: reachable by a user who has signed up but has no workspace yet —
                        // so it cannot require a workspace role, because they do not have one. This is
                        // the only authenticated area open to a user with no tenant.
                        .requestMatchers(API + "/onboarding/**").authenticated()

                        // Everything that touches tenant data. Verification is enforced here, not as a
                        // courtesy: a user's email domain is what puts them in an organisation, so an
                        // unverified address is an unproven claim. Without this line anyone could sign
                        // up as sara@nextwebspark.com and read that firm's pipeline.
                        .requestMatchers(API + "/**")
                        .access((authentication, context) -> {
                            var auth2 = authentication.get();
                            boolean ok = auth2 != null && auth2.isAuthenticated()
                                    && (!requireVerified || auth2.getAuthorities().stream()
                                    .anyMatch(a -> JwtPrincipalConverter.VERIFIED_AUTHORITY.equals(a.getAuthority())));
                            return new org.springframework.security.authorization.AuthorizationDecision(ok);
                        })

                        .anyRequest().authenticated())

                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(principalConverter)));

        // Google's redirect flow — wired only when credentials are actually configured. Spring needs a
        // ClientRegistrationRepository to build this, and there is none until someone has created an
        // OAuth client in the GCP console. Enabling it unconditionally would mean a fresh clone cannot
        // start; this way password sign-in works out of the box and the Google button simply is not
        // offered. The frontend asks GET /api/v1/auth/providers which of the two is live.
        //
        // On success the handler mints *our* tokens: Google proves who you are, it does not get to be
        // our session.
        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(login -> login.successHandler(oauthSuccessHandler));
        }

        return http.build();
    }

    /**
     * CORS. {@code allowCredentials} is what lets the browser send the refresh cookie cross-origin
     * (the SPA is on :5173, the API on :8080), and it is precisely why the origin list must be
     * explicit — a wildcard origin with credentials is forbidden by the spec, and rightly so.
     */
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
