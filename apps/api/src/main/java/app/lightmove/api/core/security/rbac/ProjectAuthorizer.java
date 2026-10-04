package app.lightmove.api.core.security.rbac;

import app.lightmove.api.core.security.model.AuthPrincipal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The project-tier guard bean behind {@code @PreAuthorize} — e.g.
 * {@code @RequireProjectPermission(ProjectAction.TEAM_MANAGE)}.
 *
 * <p>Same contract as {@link WorkspaceAuthorizer}: database re-read, enforcement by throwing,
 * controllers only. {@code @RequireProjectPermission} takes a {@link ProjectAction}, so a misspelled
 * action is a compile error; the {@code valueOf} here still refuses anything a raw expression passes.
 */
@Component("projectAuthorizer")
@RequiredArgsConstructor
public class ProjectAuthorizer {

    private final ProjectAccess access;

    public boolean can(AuthPrincipal principal, UUID projectId, String action) {
        access.requireAction(principal.userId(), principal.requireWorkspaceId(), projectId,
                ProjectAction.valueOf(action));
        return true;
    }
}
