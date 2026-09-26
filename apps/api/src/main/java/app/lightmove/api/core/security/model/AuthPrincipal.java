package app.lightmove.api.core.security.model;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import java.util.Set;
import java.util.UUID;

/**
 * Who is making this request, rebuilt from signed token claims every request — never a request
 * parameter, which is the whole of tenant isolation. The roles are coarse only, up to 15 minutes
 * stale: nothing branches on them to grant or refuse.
 *
 * @param workspaceId null before signup's organisation step; only onboarding is reachable then
 * @param roles       the workspace roles in {@code workspaceId}; empty when that is null
 */
public record AuthPrincipal(
        UUID userId,
        String email,
        UUID workspaceId,
        Set<WorkspaceRole> roles,
        boolean emailVerified
) {

    public AuthPrincipal {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /**
     * The workspace id, or a refusal. Call this — not {@link #workspaceId()} — anywhere the caller must
     * already be in a tenant, so a missing workspace fails loudly rather than becoming a {@code null} in
     * a WHERE clause that quietly matches nothing (or, worse, everything).
     *
     * <p>Throws an {@link ApiException}, not an {@link IllegalStateException}. A user who has signed up
     * but not yet joined a workspace reaching a workspace endpoint is an ordinary, expected refusal, and
     * they should get a 404. Throwing an unchecked framework exception turned it into a 500, which is us
     * telling the user we crashed when in fact we correctly declined.
     */
    public UUID requireWorkspaceId() {
        if (workspaceId == null) {
            throw new ApiException(ErrorCode.NOT_A_MEMBER, "No workspace on principal for user " + userId);
        }
        return workspaceId;
    }
}
