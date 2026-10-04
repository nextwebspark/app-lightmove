package app.lightmove.api.workspace.model;

import app.lightmove.api.workspace.constant.WorkspaceMode;

/**
 * Signup's organisation step. No email domain — it comes from the verified address, or anyone could
 * claim any firm's — and no role: the creator is ADMIN. {@code apolloAccountId} is null when typed.
 */
public record CreateWorkspaceCommand(
        String name,
        WorkspaceMode mode,
        String apolloAccountId,
        String companySize,
        String primaryRegion,
        String teamFocus
) {
}
