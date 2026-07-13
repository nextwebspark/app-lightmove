package app.lightmove.api.auth.application;

import app.lightmove.api.auth.domain.User;
import app.lightmove.api.workspace.domain.WorkspaceMember;

/**
 * Everything a successful authentication produced: the tokens, and who they belong to.
 *
 * <p>Returned by signup, login and refresh alike, so the controller never has to reopen a token it
 * just minted to find out whose it was — the services already knew.
 *
 * @param membership null when the user has an account but no organisation yet (signup step 1 done,
 *                   step 2 not). The access token then carries no tenant claim.
 */
public record AuthenticatedSession(
        TokenPair tokens,
        User user,
        WorkspaceMember membership
) {
}
