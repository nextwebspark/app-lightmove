package app.lightmove.api.core.security.rbac;

import app.lightmove.api.core.security.model.AuthPrincipal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The project-tier guard bean behind {@code @PreAuthorize} — e.g.
 * {@code @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'TEAM_MANAGE')")}.
 *
 * <p>Same contract as {@link WorkspaceAuthorizer}: database re-read, enforcement by throwing,
 * controllers only. The action string resolves through {@link ProjectAction#valueOf}, so a typo in
 * an annotation fails the first request loudly instead of silently granting nothing.
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
