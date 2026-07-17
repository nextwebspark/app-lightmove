package app.lightmove.api.core.security.config;
import app.lightmove.api.core.security.jwt.JwtPrincipalConverter;
import app.lightmove.api.core.security.service.OAuth2LoginSuccessHandler;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.handler.ProblemAccessDeniedHandler;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
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
 * <p>Two of them authenticate, and splitting <i>those</i> is the point — because LightMove authenticates
 * in two different ways, with two different threat models:
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
 * <p>The other two carry no credential at all: Actuator, fenced off onto its own socket, and the SPA,
 * which is static files.
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
     * Chain 0: Actuator, and <b>only</b> on the management port.
     *
     * <p>Actuator listens on its own loopback-bound socket (see {@code management.server.port}). Nothing
     * outside the host can reach it, so a scrape from Prometheus needs no credential — but the app port
     * must not be opened by the same rule, and matching on path alone would do exactly that:
     * {@code /actuator/prometheus} is the same path on both sockets. So the matcher checks the port the
     * request actually arrived on. Metrics are readable on 9090 and refused on 8080.
     *
     * <p>The alternative — the tenant's own {@code ROLE_ADMIN}, which is what this used to be — meant
     * every customer who created a workspace could read our metrics. A workspace role is not a system
     * role, and no amount of matcher cleverness fixes that; only a different socket does.
     *
     * <p><b>Cloud Run routes exactly one port into a container</b>, so there the two ports are set equal
     * (`MANAGEMENT_PORT=8080`) and this chain deliberately matches nothing. That is not a loophole: with
     * Actuator on the app socket, chain 3 governs it, and chain 3 permits only {@code health} and
     * {@code info} and denies the rest. Metrics stay shut either way — the fence just moves from the
     * socket to the matcher.
     */
    @Bean
    @Order(0)
    SecurityFilterChain actuatorChain(HttpSecurity http, ServerProperties server,
                                      ObjectProvider<ManagementServerProperties> management) throws Exception {
        // ObjectProvider, not a plain parameter: Spring Boot only registers ManagementServerProperties
        // when Actuator gets a management context of its own, which it only gets when its port DIFFERS
        // from the application's. Ask for the bean outright and the same-port case — the one this method
        // exists to handle, and the one Cloud Run forces — fails to inject and the application does not
        // start at all. The branch below was unreachable until this became optional.
        ManagementServerProperties properties = management.getIfAvailable();
        Integer managementPort = properties == null ? null : properties.getPort();
        int appPort = server.getPort() == null ? 8080 : server.getPort();

        // Same port for both means Actuator is on the app socket, and this chain must not exist — chain
        // 3's denyAll is the only correct answer there.
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

    /**
     * Chain 1: the cookie-authenticated auth endpoints. CSRF stays ON.
     *
     * <p>Ordered first so it claims {@code /api/v1/auth/**} before the general chain sees it.
     */
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
                // A rejected CSRF token is denied here, in the filter chain, where no
                // @RestControllerAdvice can see it. Without this it answers a bodiless 403 and the
                // SPA cannot tell "re-fetch the token and retry" from "you may not do this".
                .exceptionHandling(e -> e.accessDeniedHandler(accessDenied))

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
     * Chain 2: the SPA — its assets, and the history fallback that serves {@code index.html} for a
     * route the browser asks for directly.
     *
     * <p>The SPA is served from the same origin as the API, and that is not a packaging convenience:
     * the refresh cookie is {@code SameSite=Strict} and host-only, so a browser will only ever send it
     * back to the host that served the page. One origin is what makes the auth model work at all — it
     * is the same reason the Vite dev server proxies {@code /api} rather than pointing at :8080.
     *
     * <p>Matched by <i>exclusion</i> — everything that is not the API, Actuator, or the OAuth2 redirect
     * endpoints. The alternative, listing the SPA's routes, rots: the router grows a route, nobody
     * updates this list, and the new screen answers 401 to a user who is perfectly well logged in.
     *
     * <p>The consequence is worth stating plainly, because it is the cost of matching this way: any
     * future endpoint <b>outside</b> {@code /api/v1} is public. Every endpoint in this codebase lives
     * under {@code /api/v1}. Keep it that way — {@code SpaSecurityTest} holds that line.
     */
    @Bean
    @Order(2)
    SecurityFilterChain spaChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(request -> {
                    String path = request.getRequestURI();
                    return !path.startsWith("/api/")
                            && !path.startsWith("/actuator")
                            && !path.startsWith("/oauth2/")
                            && !path.startsWith("/login/oauth2");
                })
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // Static files. There is no state to forge a request against.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /**
     * Chain 3: everything else. Stateless bearer tokens, CSRF off (see the class note).
     */
    @Bean
    @Order(3)
    SecurityFilterChain apiChain(HttpSecurity http,
                                 // Qualified because Spring MVC's HandlerMappingIntrospector is also a
                                 // CorsConfigurationSource, so the type alone is ambiguous.
                                 @Qualifier("corsConfigurationSource") CorsConfigurationSource cors,
                                 JwtPrincipalConverter principalConverter,
                                 OAuth2LoginSuccessHandler oauthSuccessHandler,
                                 ObjectProvider<ClientRegistrationRepository> clientRegistrations,
                                 ProblemAccessDeniedHandler accessDenied,
                                 LightMoveProperties properties,
                                 Environment environment) throws Exception {
        AuthorizationManager<RequestAuthorizationContext> verified =
                verifiedEmail(properties.auth().requireVerifiedEmail());

        http
                .cors(c -> c.configurationSource(cors))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                        // Every verified-email refusal lands here. The default handler writes an empty
                        // 403 — which is how the most consequential gate in the product reached users
                        // as "That request could not be completed."
                        .accessDeniedHandler(accessDenied))

                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Liveness only. Everything else Actuator exposes — metrics, prometheus, env —
                        // lives on the management port (see application.yml) and is not routed here at
                        // all. It used to be hasRole("ADMIN"), which is the *tenant* role every
                        // workspace creator is granted: any customer could scrape our metrics.
                        // A workspace role must never double as a system-admin role.
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").denyAll()

                        // Google sign-in. Spring owns these paths.
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()

                        // Anonymous: the person clicking an invitation link out of their inbox has no
                        // account yet, and must still be shown what they are being offered. The 256-bit
                        // token in the URL is the credential, and it was mailed to the address the
                        // preview names.
                        .requestMatchers(HttpMethod.GET, API + "/onboarding/invitations/preview").permitAll()

                        // Redeeming an invitation stays verified-only, and is the one onboarding write
                        // that cannot be *held*: accepting lands you ACTIVE in a real workspace
                        // immediately, with real access to a real firm's candidate data. Holding the
                        // link is not proof of the mailbox — an invitation forwarded, or read over a
                        // shoulder, is a link in the hands of someone it was not sent to.
                        .requestMatchers(API + "/onboarding/invitations/accept").access(verified)

                        // Onboarding is the user's own signup, and they are allowed to finish it.
                        //
                        // This used to require a verified address, and that was a dead end: a user who
                        // had not yet clicked their link got a 403 in the middle of a wizard they were
                        // being asked to complete. The rule it was protecting is real — an unverified
                        // address is an unproven claim, and nothing may exist on a firm's domain on the
                        // strength of one — but a filter is the wrong place to enforce it. A filter can
                        // only refuse the request; it cannot say "hold this until you verify".
                        //
                        // So the rule moved to where it can be honoured: OnboardingService *holds* the
                        // wizard rather than executing it (see PendingOnboarding), and verification is
                        // what turns it into a workspace. Unverified users may reach these endpoints and
                        // still cannot cause a workspace, a join request, or an invitation email to
                        // exist. The gate did not weaken; it moved from the routing layer to the domain,
                        // which is the only layer that can distinguish "no" from "not yet".
                        .requestMatchers(API + "/onboarding/**").authenticated();

                    // Local-only convenience: skips the signup/workspace/refresh dance for manually
                    // testing company search. Safe to scope this loosely because app_lm_companies is
                    // shared reference data, not workspace-scoped — see CompanySearchController's class
                    // doc. Gated on `local` (the profile npm run dev actually activates, package.json)
                    // so this can never be reached in test, staging, or production — the same profile
                    // that relaxes the management port binding below.
                    if (environment.acceptsProfiles(Profiles.of("local"))) {
                        auth.requestMatchers(API + "/companies/search").permitAll();
                    }

                    // Everything that touches tenant data. Still verified-only, and this is the line
                    // that matters: an unverified user may describe their organisation, but may not
                    // read a single candidate record.
                    auth.requestMatchers(API + "/**").access(verified)

                        .anyRequest().authenticated();
                })

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
     * Authenticated, and — unless the deployment has opted out — holding a verified email address.
     *
     * <p>This guards two different things for the same reason. Tenant data, obviously. But also the
     * onboarding <i>writes</i>, which are what bind a user to an organisation in the first place: the
     * email domain is our only evidence that someone works at a firm, and an unverified address is an
     * unproven claim. Let an unverified user through and they can sign up as
     * {@code victim@realfirm.com}, never open the mailbox, and become ADMIN of a workspace bound to a
     * domain that isn't theirs.
     *
     * <p>Extracted rather than inlined at each matcher so there is exactly one definition of "verified"
     * to get wrong.
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
