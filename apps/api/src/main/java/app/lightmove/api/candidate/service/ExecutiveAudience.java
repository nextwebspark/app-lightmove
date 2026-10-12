package app.lightmove.api.candidate.service;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Who is reading a position's executives — the one place the client-visibility boundary is decided (V131).
 * A signed-in caller who reads the position without working it ({@code WORK_VIEW} but not
 * {@code WORK_EXECUTE}: a client seat) never sees an executive hidden from the client, on any read that
 * goes through {@code CandidateService}: the list, the drawer, the talent map, the report and the export.
 *
 * <p>Re-read from the database on every call, as the guard beans are. Anything else — staff, a workspace
 * API key, an MCP connection, a background job — holds no client seat and sees everyone: a key and a
 * connection are staff's by construction.
 */
@Component
@RequiredArgsConstructor
public class ExecutiveAudience {

    private final ProjectAccess projectAccess;

    public boolean seesOnlyClientVisible(UUID workspaceId, UUID projectId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return false;
        }
        return !projectAccess.projectsWithAction(principal.userId(), workspaceId, List.of(projectId),
                ProjectAction.WORK_EXECUTE).contains(projectId);
    }
}
