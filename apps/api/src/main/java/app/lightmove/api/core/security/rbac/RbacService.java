package app.lightmove.api.core.security.rbac;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Catalog lookups — the bridge from code-side role names to the seeded {@link Role} rows that
 * assignments actually reference.
 *
 * <p>A missing row is an {@link IllegalStateException}, not an {@code ApiException}: the catalog is
 * seeded by migration, so its absence is a broken deployment, never a user mistake.
 * {@code RbacCatalogTest} makes that a red build instead of a runtime surprise.
 */
@Service
@RequiredArgsConstructor
public class RbacService {

    private final RoleRepository roles;

    public Role role(WorkspaceRole role) {
        return require(RoleScope.WORKSPACE, role.name());
    }

    public Role role(ProjectRole role) {
        return require(RoleScope.PROJECT, role.name());
    }

    public Set<Role> workspaceRoles(Collection<WorkspaceRole> names) {
        return names.stream().map(this::role).collect(Collectors.toSet());
    }

    public Set<Role> projectRoles(Collection<ProjectRole> names) {
        return names.stream().map(this::role).collect(Collectors.toSet());
    }

    private Role require(RoleScope scope, String name) {
        return roles.findByScopeAndName(scope, name)
                .orElseThrow(() -> new IllegalStateException(
                        "Role catalog is missing %s/%s — the V6 seeds did not run".formatted(scope, name)));
    }
}
