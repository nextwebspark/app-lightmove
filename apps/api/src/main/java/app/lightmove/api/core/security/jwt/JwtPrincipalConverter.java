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
 * Turns a verified JWT into the {@link AuthPrincipal}; the workspace id comes from the signed claim, never
 * a request parameter. {@code ROLE_*} authorities are coarse route material only — every role-sensitive
 * decision re-reads the database.
 */
@Component
public class JwtPrincipalConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String VERIFIED_AUTHORITY = "SCOPE_VERIFIED";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString("email");
        boolean emailVerified = Boolean.TRUE.equals(jwt.getClaim("emailVerified"));

        // Absent before onboarding: such a user reaches only the onboarding endpoints.
        String workspaceClaim = jwt.getClaimAsString("wsId");
        UUID workspaceId = workspaceClaim == null ? null : UUID.fromString(workspaceClaim);

        AuthPrincipal principal = new AuthPrincipal(userId, email, workspaceId, roles(jwt), emailVerified);
        return new JwtPrincipalAuthentication(jwt, principal, authorities(principal));
    }

    /** Also reads a legacy single {@code role} claim as a one-element set, so an old token does not 500. */
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

    /** Carries the {@link AuthPrincipal}, so {@code @AuthenticationPrincipal AuthPrincipal} resolves. */
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
