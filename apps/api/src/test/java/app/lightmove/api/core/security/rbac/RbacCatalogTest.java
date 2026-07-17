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

    /**
     * The catalog tests above prove the role and action <i>names</i> line up; this one proves the
     * <i>grant map</i> does. It matters because the V6 seed joins role and action by string literal — a
     * typo (`'PROJECT_BROWS'`) seeds <b>zero rows, not an error</b>, silently stripping a permission while
     * the build stays green. Pinning each role's grants to a code-side expectation turns that into a red
     * build, which is what CLAUDE.md claims the catalog test does.
     */
    @Test
    @DisplayName("each role grants exactly the actions the seed intends — a mis-seeded grant fails here")
    @Transactional(readOnly = true)
    void roleGrantsMatchTheSeededMap() {
        grantsAre(RoleScope.WORKSPACE, "ADMIN", "WORKSPACE_MANAGE", "MEMBER_MANAGE", "MEMBER_INVITE",
                "PROJECT_CREATE", "PROJECT_BROWSE", "CLIENT_RECORD_MANAGE");
        grantsAre(RoleScope.WORKSPACE, "MEMBER", "PROJECT_CREATE", "PROJECT_BROWSE", "CLIENT_RECORD_MANAGE");
        grantsAre(RoleScope.WORKSPACE, "CLIENT");
        grantsAre(RoleScope.PROJECT, "ADMIN", "PROJECT_EDIT", "TEAM_MANAGE", "WORK_EXECUTE",
                "POSITION_UNLOCK");
        grantsAre(RoleScope.PROJECT, "LEAD", "PROJECT_EDIT", "WORK_EXECUTE");
        grantsAre(RoleScope.PROJECT, "RESEARCHER", "WORK_EXECUTE");
        grantsAre(RoleScope.PROJECT, "CLIENT");
    }

    private void grantsAre(RoleScope scope, String roleName, String... expectedActions) {
        Role role = roles.findByScopeAndName(scope, roleName)
                .orElseThrow(() -> new AssertionError("no seeded role " + scope + "/" + roleName));
        assertThat(names(role.getActions().stream().toList(), Action::getName))
                .as("grants for %s/%s", scope, roleName)
                .containsExactlyInAnyOrder(expectedActions);
    }

    private static <T> List<String> names(List<T> rows, java.util.function.Function<T, String> name) {
        return rows.stream().map(name).toList();
    }

    private static List<String> names(Enum<?>[] constants) {
        return Arrays.stream(constants).map(Enum::name).toList();
    }
}
