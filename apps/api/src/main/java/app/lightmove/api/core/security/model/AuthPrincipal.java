package app.lightmove.api.core.security.model;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import java.util.Set;
import java.util.UUID;

/**
 * Who is making this request, and in which workspace.
 *
 * <p>Rebuilt from the access token's claims on every request — never from a request parameter. That
 * distinction is the whole of tenant isolation: {@link #workspaceId()} is a claim we signed, so a
 * caller cannot name someone else's workspace and be served their data.
 *
 * <p>The roles here are <b>coarse material only</b> — they were minted up to 15 minutes ago. Anything
 * role-sensitive re-reads the database through the rbac access services; nothing should branch on
 * this set to grant or refuse.
 *
 * @param workspaceId null before the user has completed signup step 2 — they exist, but have no
 *                    tenant yet, and may only reach the onboarding endpoints.
 * @param roles       the user's workspace roles in {@code workspaceId}; empty when that is null.
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

    /** True once the user has a workspace and may use the app proper. */
    public boolean hasWorkspace() {
        return workspaceId != null;
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
