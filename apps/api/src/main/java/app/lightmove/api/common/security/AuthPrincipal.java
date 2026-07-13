package app.lightmove.api.common.security;

import app.lightmove.api.common.error.ApiException;
import app.lightmove.api.common.error.ErrorCode;
import app.lightmove.api.workspace.domain.WorkspaceRole;
import java.util.UUID;

/**
 * Who is making this request, and in which workspace.
 *
 * <p>Rebuilt from the access token's claims on every request — never from a request parameter. That
 * distinction is the whole of tenant isolation: {@link #workspaceId()} is a claim we signed, so a
 * caller cannot name someone else's workspace and be served their data.
 *
 * @param workspaceId null before the user has completed signup step 2 — they exist, but have no
 *                    tenant yet, and may only reach the onboarding endpoints.
 * @param role        the user's role in {@code workspaceId}; null when that is null.
 */
public record AuthPrincipal(
        UUID userId,
        String email,
        UUID workspaceId,
        WorkspaceRole role,
        boolean emailVerified
) {

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
     * but not yet joined a workspace — or whose request to join is still pending — reaching a
     * workspace endpoint is an ordinary, expected refusal, and they should get a 404. Throwing an
     * unchecked framework exception turned it into a 500, which is us telling the user we crashed when
     * in fact we correctly declined.
     */
    public UUID requireWorkspaceId() {
        if (workspaceId == null) {
            throw new ApiException(ErrorCode.NOT_A_MEMBER, "No workspace on principal for user " + userId);
        }
        return workspaceId;
    }
}
