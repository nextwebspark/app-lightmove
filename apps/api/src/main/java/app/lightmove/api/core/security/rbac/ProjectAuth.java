package app.lightmove.api.core.security.rbac;

import app.lightmove.api.core.security.model.AuthPrincipal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The project-tier guard bean behind {@code @PreAuthorize} — e.g.
 * {@code @PreAuthorize("@projectAuth.can(principal, #projectId, 'TEAM_MANAGE')")}.
 *
 * <p>Same contract as {@link WorkspaceAuth}: database re-read, enforcement by throwing, controllers
 * only. The action string resolves through {@link ProjectAction#valueOf}, so a typo in an annotation
 * fails the first request loudly instead of silently granting nothing.
 */
@Component("projectAuth")
@RequiredArgsConstructor
public class ProjectAuth {

    private final ProjectAccess access;

    public boolean can(AuthPrincipal principal, UUID projectId, String action) {
        access.requireAction(principal.userId(), principal.requireWorkspaceId(), projectId,
                ProjectAction.valueOf(action));
        return true;
    }
}
