package app.lightmove.api.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The migrated schema leaves no tenant table unprotected, and a table added without row-level security — one with
 * a workspace of its own, or a child of a tenant table with none — is named until it is forced and given a policy.
 */
@IntegrationTest
class TenantTableGuardIntegrationTest {

    @Autowired TenantTableGuard guard;
    @Autowired @FixtureJdbc JdbcTemplate fixture;

    @AfterEach
    void dropTheProbes() {
        fixture.execute("DROP TABLE IF EXISTS app_lm_rls_guard_probe_child, app_lm_rls_guard_probe");
    }

    @Test
    @DisplayName("every tenant table of the migrated schema is forced and has a policy")
    void theSchemaIsCovered() {
        assertThat(guard.faults()).isEmpty();
    }

    @Test
    @DisplayName("a new table carrying a workspace is named until it is protected")
    void aWorkspaceTableIsNamed() {
        fixture.execute("CREATE TABLE app_lm_rls_guard_probe (id uuid PRIMARY KEY, workspace_id uuid NOT NULL)");

        assertThat(guard.faults()).singleElement().asString().contains("app_lm_rls_guard_probe");

        fixture.execute("""
                ALTER TABLE app_lm_rls_guard_probe ENABLE ROW LEVEL SECURITY;
                ALTER TABLE app_lm_rls_guard_probe FORCE ROW LEVEL SECURITY;
                CREATE POLICY app_lm_tenant ON app_lm_rls_guard_probe USING (app_lm_tenant_visible(workspace_id))""");

        assertThat(guard.faults()).isEmpty();
    }

    @Test
    @DisplayName("a new child of a tenant table, with no workspace of its own, is named too")
    void aChildTableIsNamed() {
        fixture.execute("""
                CREATE TABLE app_lm_rls_guard_probe_child (
                    id uuid PRIMARY KEY, project_id uuid NOT NULL REFERENCES app_lm_project (id))""");

        assertThat(guard.faults()).singleElement().asString()
                .contains("app_lm_rls_guard_probe_child");

        fixture.execute("""
                ALTER TABLE app_lm_rls_guard_probe_child ENABLE ROW LEVEL SECURITY;
                ALTER TABLE app_lm_rls_guard_probe_child FORCE ROW LEVEL SECURITY""");

        assertThat(guard.faults()).as("forced but with no policy, every row is refused, which is no design")
                .singleElement().asString().contains("app_lm_rls_guard_probe_child");
    }
}
