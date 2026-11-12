package app.lightmove.api.core.security.rbac;

import app.lightmove.api.core.security.model.AuthPrincipal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The project-tier guard bean behind {@code @RequireProjectPermission(ProjectAction.TEAM_MANAGE)}: same contract as
 * {@link WorkspaceAuthorizer} (database re-read, throws, controllers only); {@code valueOf} still refuses a raw
 * expression's misspelled {@link ProjectAction}.
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
