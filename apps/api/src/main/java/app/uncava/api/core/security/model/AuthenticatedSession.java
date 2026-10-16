package app.uncava.api.core.security.model;
import app.uncava.api.core.security.token.TokenPair;

import app.uncava.api.workspace.model.WorkspaceMember;

/**
 * What signup, login and refresh produce: the tokens and whose they are.
 *
 * @param membership null before the user has an organisation; the access token then has no tenant claim
 */
public record AuthenticatedSession(
        TokenPair tokens,
        User user,
        WorkspaceMember membership
) {
}
