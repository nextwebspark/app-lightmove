package app.lightmove.api.core.security.model;
import app.lightmove.api.core.security.token.TokenPair;

import app.lightmove.api.workspace.model.WorkspaceMember;
import java.util.UUID;

/**
 * What signup, login and refresh produce: the tokens and whose they are.
 *
 * @param membership null before the user has an organisation; the access token then has no tenant claim
 */
public record AuthenticatedSession(
        TokenPair tokens,
        User user,
        WorkspaceMember membership
) implements SignInOutcome {

    /** The workspace the session opened into, or null — a sign-in is stamped with it so only that firm's log shows it. */
    public UUID workspaceId() {
        return membership == null ? null : membership.getWorkspaceId();
    }
}
