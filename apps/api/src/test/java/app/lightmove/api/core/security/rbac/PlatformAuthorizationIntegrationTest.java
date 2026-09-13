package app.lightmove.api.core.security.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.positiontemplate.PositionTemplateFlowSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * The platform tier: who reaches the shared library, how they learn they can, and that the role opens
 * nothing inside any workspace.
 */
@IntegrationTest
class PlatformAuthorizationIntegrationTest extends PositionTemplateFlowSupport {

    @Test
    @DisplayName("a workspace admin is not a super admin, and the library endpoints do not admit to existing")
    void workspaceAdminCannotReachTheLibrary() throws Exception {
        Firm firm = firm("Probe Firm", "alok");

        mvc.perform(get(LIBRARY).header("Authorization", bearer(firm.token())))
                .andExpect(status().isNotFound());
        expectRefused(404, "NOT_FOUND", putJson(firm.token(), LIBRARY + "/chief-financial-officer",
                templateRequest("Chief Financial Officer", uniqueKeyword(), "Finance", 0L)));
        assertThat(getJson(firm.token(), "/api/v1/auth/me").get("platformActions").size()).isZero();
    }

    @Test
    @DisplayName("a granted super admin reaches the library on the token they already hold, and /me says so")
    void grantedSuperAdminReachesTheLibrary() throws Exception {
        Firm owner = firm("Owner Firm", "owner");
        grantSuperAdmin(owner.email());

        assertThat(getJson(owner.token(), LIBRARY).size()).isGreaterThanOrEqualTo(17);
        assertThat(getJson(owner.token(), "/api/v1/auth/me").get("platformActions").get(0).asText())
                .isEqualTo("TEMPLATE_LIBRARY_MANAGE");
    }

    @Test
    @DisplayName("a platform action seeded by a newer build is left out of /me rather than failing every auth response")
    void platformActionUnknownToThisBuildIsSkipped() throws Exception {
        String owner = superAdmin();
        db.update("insert into app_lm_action (scope, name, description) "
                + "values ('PLATFORM', 'FROM_A_NEWER_BUILD', 'Seeded by a migration this build predates')");
        try {
            db.update("""
                    insert into app_lm_role_action (role_id, action_id)
                    select r.id, a.id from app_lm_role r, app_lm_action a
                    where r.scope = 'PLATFORM' and r.name = 'SUPER_ADMIN'
                      and a.scope = 'PLATFORM' and a.name = 'FROM_A_NEWER_BUILD'
                    """);

            JsonNode actions = getJson(owner, "/api/v1/auth/me").get("platformActions");
            assertThat(actions.size()).isEqualTo(1);
            assertThat(actions.get(0).asText()).isEqualTo("TEMPLATE_LIBRARY_MANAGE");
        } finally {
            // The catalog is shared by every suite and RbacCatalogTest holds it to the enums.
            db.update("delete from app_lm_role_action where action_id in "
                    + "(select id from app_lm_action where name = 'FROM_A_NEWER_BUILD')");
            db.update("delete from app_lm_action where name = 'FROM_A_NEWER_BUILD'");
        }
    }

    @Test
    @DisplayName("the platform role opens nothing inside another firm's workspace")
    void superAdminReadsNoTenantData() throws Exception {
        Firm neighbour = firm("Neighbour Firm", "sara");
        String theirProject = createProject(neighbour.token(), "Chief Financial Officer");
        String owner = superAdmin();

        mvc.perform(get("/api/v1/projects/" + theirProject + "/position").header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
    }
}
