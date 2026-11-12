package app.lightmove.api.core.security.model;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.core.tenant.TenantPrincipal;
import java.util.Set;
import java.util.UUID;

/**
 * Who is making this request, rebuilt from signed token claims — never a request parameter. Roles are coarse and
 * up to 15 minutes stale: nothing branches on them to grant or refuse.
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
) implements TenantPrincipal {

    public AuthPrincipal {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /**
     * The workspace id, or a 404 {@link ApiException} — never a {@code null} in a WHERE clause. Not an
     * {@link IllegalStateException}: that turned an ordinary refusal of a user with no workspace into a 500.
     */
    public UUID requireWorkspaceId() {
        if (workspaceId == null) {
            throw new ApiException(ErrorCode.NOT_A_MEMBER, "No workspace on principal for user " + userId);
        }
        return workspaceId;
    }
}
