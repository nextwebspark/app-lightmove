package app.lightmove.api.auth.api;

import app.lightmove.api.auth.api.dto.AuthDtos.AuthResponse;
import app.lightmove.api.auth.api.dto.AuthDtos.UserResponse;
import app.lightmove.api.auth.api.dto.AuthDtos.WorkspaceSummary;
import app.lightmove.api.auth.application.TokenPair;
import app.lightmove.api.auth.domain.User;
import app.lightmove.api.workspace.domain.Workspace;
import app.lightmove.api.workspace.domain.WorkspaceMember;
import app.lightmove.api.workspace.infrastructure.PendingOnboardingRepository;
import app.lightmove.api.workspace.infrastructure.WorkspaceRepository;
import org.springframework.stereotype.Component;

/**
 * Builds the response body that login, signup, refresh and {@code /me} all return.
 *
 * <p>Extracted so those four cannot drift apart. They are the same shape by design — the SPA has one
 * function that consumes an authenticated session, and it should not care which endpoint produced it.
 */
@Component
public class AuthResponseAssembler {

    private final WorkspaceRepository workspaces;
    private final PendingOnboardingRepository pending;

    public AuthResponseAssembler(WorkspaceRepository workspaces, PendingOnboardingRepository pending) {
        this.workspaces = workspaces;
        this.pending = pending;
    }

    public AuthResponse assemble(TokenPair tokens, User user, WorkspaceMember membership) {
        return new AuthResponse(
                tokens.accessToken(),
                tokens.accessTokenTtl().toSeconds(),
                user(user, membership));
    }

    public UserResponse user(User user, WorkspaceMember membership) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getTitle(),
                user.getAvatarUrl(),
                user.isEmailVerified(),
                workspaceSummary(membership),
                // Only meaningful for an unverified user: verifying materialises the wizard, so a
                // verified one never has anything held.
                !user.isEmailVerified() && pending.findByUserId(user.getId()).isPresent());
    }

    /** Null when the user has signed up but not yet created their organisation. */
    private WorkspaceSummary workspaceSummary(WorkspaceMember membership) {
        if (membership == null || !membership.isActive()) {
            return null;
        }
        return workspaces.findById(membership.getWorkspaceId())
                .map(workspace -> toSummary(workspace, membership))
                .orElse(null);
    }

    private static WorkspaceSummary toSummary(Workspace workspace, WorkspaceMember membership) {
        return new WorkspaceSummary(
                workspace.getId(),
                workspace.getName(),
                workspace.getSlug(),
                workspace.getLogoMark(),
                workspace.getEmailDomain(),
                membership.getRole());
    }
}
