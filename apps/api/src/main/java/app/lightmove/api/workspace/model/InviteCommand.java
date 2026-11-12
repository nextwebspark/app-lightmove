package app.lightmove.api.workspace.model;

import app.lightmove.api.core.security.rbac.WorkspaceRole;

/** One invitee: an address, and the role they will hold — MEMBER when none is named. */
public record InviteCommand(
        String email,
        WorkspaceRole role
) {

    public InviteCommand {
        if (role == null) {
            role = WorkspaceRole.MEMBER;
        }
    }
}
