package app.lightmove.api.core.security.service;

import app.lightmove.api.core.security.dto.AuthResponse;
import app.lightmove.api.core.security.dto.UserResponse;
import app.lightmove.api.core.security.membership.WorkspaceMember;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.token.TokenPair;
import java.util.UUID;

/**
 * Builds the response body that login, signup, refresh and {@code /me} all return.
 *
 * <p>One builder so those four cannot drift apart. They are the same shape by design — the SPA has one
 * function that consumes an authenticated session, and it should not care which endpoint produced it. Implemented
 * on the workspace's side, which owns the workspaces and invitations the response lists.
 */
public interface AuthResponseAssembler {

    AuthResponse assemble(TokenPair tokens, User user, WorkspaceMember membership);

    /** @param membership the workspace this <i>session</i> is in, or null — never "the user's", of several */
    UserResponse user(User user, WorkspaceMember membership);

    /** As a member of exactly this workspace, or none — the session's, or one just created or joined. */
    UserResponse userIn(UUID userId, UUID workspaceId);
}
