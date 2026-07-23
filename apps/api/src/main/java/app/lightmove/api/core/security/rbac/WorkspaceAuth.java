package app.lightmove.api.core.security.rbac;

import app.lightmove.api.core.security.model.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The workspace-tier guard bean behind {@code @PreAuthorize} — e.g.
 * {@code @PreAuthorize("@workspaceAuth.can(principal, 'MEMBER_INVITE')")}.
 *
 * <p>Every method re-reads the database through {@link WorkspaceAccess}; the JWT's roles claim is
 * coarse material only, up to 15 minutes stale. Methods return {@code true} (the SpEL contract) but
 * enforce by <b>throwing</b> {@code ApiException} — never by returning false — so a denial keeps its
 * precise error code and the 404 masking for non-members, instead of collapsing into a generic 403.
 * Spring rethrows runtime exceptions from SpEL-invoked bean methods unwrapped, so they land in
 * {@code GlobalExceptionHandler.handleApiException} like any imperative check's.
 *
 * <p>Annotations live on <b>controllers only</b>. Services reachable outside a request's
 * SecurityContext — everything {@code PendingOnboardingMaterialiser} calls with its synthetic
 * principal — keep imperative checks, because method security would evaluate the wrong (or no)
 * authentication there.
 */
@Component("workspaceAuth")
@RequiredArgsConstructor
public class WorkspaceAuth {

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
