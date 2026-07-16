package app.lightmove.api.workspace.model;

/**
 * Signup step 2 — "About your organization".
 *
 * <p>Note what is <i>not</i> here: the email domain. It is not the user's to choose — it is taken from
 * the address they signed up with and already verified. Letting a client supply it would mean anyone
 * could claim any firm's domain.
 *
 * @param jobTitle the mockup's "Your role" (Partner / Consultant / Researcher / Operations). It is a
 *                 job title, and lands on the user. It is <b>not</b> the workspace role — the person
 *                 who creates the organisation is always its ADMIN.
 */
public record CreateWorkspaceCommand(
        String name,
        String companySize,
        String primaryRegion,
        String jobTitle,
        String teamFocus
) {
}
