package app.lightmove.api.core.security.rbac;

import app.lightmove.api.core.security.model.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The workspace-tier {@code @PreAuthorize} guard, re-reading the database via {@link WorkspaceAccess}.
 * It enforces by <b>throwing</b> {@code ApiException}, never returning false, so a denial keeps its
 * code and 404 masking. Controllers only: services reached outside a SecurityContext stay imperative.
 */
@Component("workspaceAuthorizer")
@RequiredArgsConstructor
public class WorkspaceAuthorizer {

    private final WorkspaceAccess access;

    /** May the caller perform this {@link WorkspaceAction} (by name) in their workspace? */
    public boolean can(AuthPrincipal principal, String action) {
        access.requireAction(principal.userId(), principal.requireWorkspaceId(),
                WorkspaceAction.valueOf(action));
        return true;
    }

    /** Active, non-pure-client membership — the gate on staff-facing reads. */
    public boolean staff(AuthPrincipal principal) {
        access.requireStaff(principal.userId(), principal.requireWorkspaceId());
        return true;
    }

    /**
     * Any active membership, client included — the gate on the project list, whose service scopes the
     * result (staff see every mandate; a client sees only the ones they are attached to). Existence of a
     * list is not secret; its contents are, and the scoping is where that is enforced.
     */
    public boolean member(AuthPrincipal principal) {
        access.requireActiveMember(principal.userId(), principal.requireWorkspaceId());
        return true;
    }
}
