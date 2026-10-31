package app.lightmove.api;

import app.lightmove.api.core.tenant.WorkspaceScope;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * For a test that calls the application's own beans directly rather than through a request: no principal is signed
 * in, so the call names the workspace it works for, as a scheduled job would — here, the workspace of the project or
 * person it is about, read from above row-level security.
 */
public final class TestWorkspaceScopes {

    private TestWorkspaceScopes() {
    }

    public static <T> T inProjectsWorkspace(JdbcTemplate fixture, UUID projectId, Supplier<T> work) {
        return WorkspaceScope.call(workspaceOfProject(fixture, projectId), work);
    }

    public static void inProjectsWorkspace(JdbcTemplate fixture, UUID projectId, Runnable work) {
        WorkspaceScope.run(workspaceOfProject(fixture, projectId), work);
    }

    public static UUID workspaceOfProject(JdbcTemplate fixture, UUID projectId) {
        return fixture.queryForObject("SELECT workspace_id FROM app_lm_project WHERE id = ?", UUID.class, projectId);
    }
}
