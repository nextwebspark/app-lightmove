package app.lightmove.api.workspace.model;

/**
 * Signup's organisation step. No email domain — it comes from the verified address, or anyone could
 * claim any firm's — and no role: the creator is ADMIN. {@code apolloAccountId} is null when typed.
 */
public record CreateWorkspaceCommand(
        String name,
        String apolloAccountId,
        String companySize,
        String primaryRegion,
        String teamFocus
) {
}
