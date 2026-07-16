package app.lightmove.api.core.security.rbac;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row of the role catalog — the database side of the RBAC model.
 *
 * <p>Read-mostly reference data, seeded by migration. Adding a role is an INSERT plus a constant in
 * {@link WorkspaceRole} or {@link ProjectRole}; what the role grants is entirely the
 * {@code app_lm_role_action} mapping, which can change without a redeploy.
 */
@Entity
@Table(name = "app_lm_role")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RoleScope scope;

    @Column(nullable = false, length = 32)
    private String name;

    @Column
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "app_lm_role_action",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "action_id"))
    private Set<Action> actions;

    /**
     * Builds an unsaved catalog row. Production rows come from migration seeds — this exists for unit
     * tests exercising the entities that hold roles, and for future admin tooling.
     */
    public static Role of(RoleScope scope, String name, String description) {
        Role role = new Role();
        role.scope = scope;
        role.name = name;
        role.description = description;
        role.actions = Set.of();
        return role;
    }

    /** Whether this row is the given code-side workspace role. */
    public boolean is(WorkspaceRole role) {
        return scope == RoleScope.WORKSPACE && name.equals(role.name());
    }

    /** Whether this row is the given code-side project role. */
    public boolean is(ProjectRole role) {
        return scope == RoleScope.PROJECT && name.equals(role.name());
    }
}
