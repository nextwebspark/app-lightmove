package app.lightmove.api.core.security.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.IntegrationTest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The catalog and the code must name the same things. The database owns what a role <i>grants</i>;
 * these enums own how code <i>refers</i> to roles and actions — and if the two drift, authorisation
 * silently stops matching. This test turns that drift into a red build.
 */
@IntegrationTest
class RbacCatalogTest {

    @Autowired RoleRepository roles;
    @Autowired ActionRepository actions;

    @Test
    @DisplayName("every workspace/project role enum constant is seeded, and nothing extra is")
    void roleCatalogMatchesTheEnums() {
        assertThat(names(roles.findByScope(RoleScope.WORKSPACE), Role::getName))
                .containsExactlyInAnyOrderElementsOf(names(WorkspaceRole.values()));
        assertThat(names(roles.findByScope(RoleScope.PROJECT), Role::getName))
                .containsExactlyInAnyOrderElementsOf(names(ProjectRole.values()));
    }

    @Test
    @DisplayName("every action enum constant is seeded, and nothing extra is")
    void actionCatalogMatchesTheEnums() {
        assertThat(names(actions.findByScope(RoleScope.WORKSPACE), Action::getName))
                .containsExactlyInAnyOrderElementsOf(names(WorkspaceAction.values()));
        assertThat(names(actions.findByScope(RoleScope.PROJECT), Action::getName))
                .containsExactlyInAnyOrderElementsOf(names(ProjectAction.values()));
    }

    @Test
    @DisplayName("CLIENT roles grant nothing — the portal phase has not shipped")
    @Transactional(readOnly = true) // walking role.getActions() needs an open session
    void clientRolesAreEmpty() {
        assertThat(roles.findByScopeAndName(RoleScope.WORKSPACE, WorkspaceRole.CLIENT.name()))
                .hasValueSatisfying(role -> assertThat(role.getActions()).isEmpty());
        assertThat(roles.findByScopeAndName(RoleScope.PROJECT, ProjectRole.CLIENT.name()))
                .hasValueSatisfying(role -> assertThat(role.getActions()).isEmpty());
    }

    private static <T> List<String> names(List<T> rows, java.util.function.Function<T, String> name) {
        return rows.stream().map(name).toList();
    }

    private static List<String> names(Enum<?>[] constants) {
        return Arrays.stream(constants).map(Enum::name).toList();
    }
}
