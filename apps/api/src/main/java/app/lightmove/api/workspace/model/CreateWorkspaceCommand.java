package app.lightmove.api.workspace.model;

/**
 * Signup step 2 — "About your organization".
 *
 * <p>Note what is <i>not</i> here: the email domain, nor a role. The domain is not the user's to
 * choose — it is taken from the address they signed up with and already verified, and letting a client
 * supply it would mean anyone could claim any firm's domain. And the person who creates the
 * organisation is always its ADMIN, so there is no role to pick.
 */
public record CreateWorkspaceCommand(
        String name,
        String companySize,
        String primaryRegion,
        String teamFocus
) {
}
