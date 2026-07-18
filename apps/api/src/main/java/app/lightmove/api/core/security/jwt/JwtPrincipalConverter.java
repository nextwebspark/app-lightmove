package app.lightmove.api.core.security.jwt;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Turns a verified JWT into the {@link AuthPrincipal} the application actually reasons about.
 *
 * <p>By the time this runs, Spring Security has already checked the signature, the expiry and the
 * issuer. So the claims can be trusted — which is exactly why the workspace id must arrive as a claim
 * and never as a request parameter.
 *
 * <p>Two kinds of authority are derived, and the distinction matters:
 *
 * <ul>
 *   <li>{@code ROLE_ADMIN} / {@code ROLE_MEMBER} (one per held workspace role) — <b>coarse route
 *       material only</b>. Every role-sensitive decision re-reads the database through the rbac guard
 *       beans, because these were minted up to 15 minutes ago.
 *   <li>{@code SCOPE_VERIFIED} — whether you have proved you own your email address. Since the email
 *       domain is what places a user in an organisation, an unverified user has proved nothing, and
 *       the filter chain keeps them out of every workspace endpoint.
 * </ul>
 */
@Component
public class JwtPrincipalConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /** Granted only to users who have clicked their verification link. */
    public static final String VERIFIED_AUTHORITY = "SCOPE_VERIFIED";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString("email");
        boolean emailVerified = Boolean.TRUE.equals(jwt.getClaim("emailVerified"));

        // Absent for a user who has signed up but not yet created their workspace. They exist, they
        // can authenticate, and they can reach only the onboarding endpoints.
        String workspaceClaim = jwt.getClaimAsString("wsId");
        UUID workspaceId = workspaceClaim == null ? null : UUID.fromString(workspaceClaim);

        AuthPrincipal principal = new AuthPrincipal(userId, email, workspaceId, roles(jwt), emailVerified);
        return new JwtPrincipalAuthentication(jwt, principal, authorities(principal));
    }

    /**
     * Multi-role claim, with one release of tolerance: tokens minted before the RBAC change carry a
     * single {@code role} string instead of a {@code roles} array. They die within the 15-minute
     * access TTL, but until then they must not 500 — read the old shape as a one-element set.
     */
    private static Set<WorkspaceRole> roles(Jwt jwt) {
        List<String> claim = jwt.getClaimAsStringList("roles");
        if (claim == null) {
            String legacy = jwt.getClaimAsString("role");
            claim = legacy == null ? List.of() : List.of(legacy);
        }
        return claim.isEmpty()
                ? Set.of()
                : claim.stream().map(WorkspaceRole::valueOf)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(WorkspaceRole.class)));
    }

    private static Collection<GrantedAuthority> authorities(AuthPrincipal principal) {
        List<GrantedAuthority> authorities = new ArrayList<>(principal.roles().size() + 1);
        principal.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority(role.authority())));
        if (principal.emailVerified()) {
            authorities.add(new SimpleGrantedAuthority(VERIFIED_AUTHORITY));
        }
        return authorities;
    }

    /**
     * Carries the {@link AuthPrincipal} as the principal, so {@code CurrentUser.require()} returns the
     * domain type rather than a raw {@link Jwt} that every call site would have to re-parse.
     */
    static final class JwtPrincipalAuthentication extends AbstractAuthenticationToken {

        private final transient Jwt jwt;
        private final transient AuthPrincipal principal;

        JwtPrincipalAuthentication(Jwt jwt, AuthPrincipal principal, Collection<GrantedAuthority> authorities) {
            super(authorities);
            this.jwt = jwt;
            this.principal = principal;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return jwt;
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }

        @Override
        public String getName() {
            return principal.userId().toString();
        }
    }
}
