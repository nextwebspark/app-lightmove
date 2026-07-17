package app.lightmove.api.workspace.model;

import app.lightmove.api.core.security.rbac.WorkspaceRole;

/** One row of signup step 3: an address, and the role they will hold. */
public record InviteCommand(
        String email,
        WorkspaceRole role
) {
}
