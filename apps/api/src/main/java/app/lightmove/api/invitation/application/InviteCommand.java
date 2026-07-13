package app.lightmove.api.invitation.application;

import app.lightmove.api.workspace.domain.WorkspaceRole;

/** One row of signup step 3: an address, and the role they will hold. */
public record InviteCommand(
        String email,
        WorkspaceRole role
) {
}
