package app.lightmove.api.core.security.controller;

import app.lightmove.api.core.security.dto.AuthDtos.AuthResponse;
import app.lightmove.api.core.security.dto.AuthDtos.PendingInvitationSummary;
import app.lightmove.api.core.security.dto.AuthDtos.UserResponse;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.core.security.token.TokenPair;
import app.lightmove.api.workspace.constant.InvitationStatus;
import app.lightmove.api.workspace.dto.WorkspaceDtos.WorkspaceSummary;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.InvitationRepository;
import app.lightmove.api.workspace.repository.PendingOnboardingRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Builds the response body that login, signup, refresh and {@code /me} all return.
 *
 * <p>Extracted so those four cannot drift apart. They are the same shape by design — the SPA has one
 * function that consumes an authenticated session, and it should not care which endpoint produced it.
 */
@Component
@RequiredArgsConstructor
public class AuthResponseAssembler {

    private final WorkspaceRepository workspaces;
    private final PendingOnboardingRepository pendingOnboardings;
    private final InvitationRepository invitations;

    public AuthResponse assemble(TokenPair tokens, User user, WorkspaceMember membership) {
        return new AuthResponse(
                tokens.accessToken(),
                tokens.accessTokenTtl().toSeconds(),
                user(user, membership));
    }

    public UserResponse user(User user, WorkspaceMember membership) {
        WorkspaceSummary workspace = workspaceSummary(membership);
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getTitle(),
                user.getAvatarUrl(),
                user.isEmailVerified(),
                workspace,
                // Only meaningful for an unverified user: verifying materialises the wizard, so a
                // verified one never has anything held.
                !user.isEmailVerified() && pendingOnboardings.findByUserId(user.getId()).isPresent(),
                workspace == null ? pendingInvitation(user) : null);
    }

    /**
     * The caller's own outstanding invitation, so routing can be derived from the server instead of a
     * tab's sessionStorage — an invitee who verifies in a fresh tab must land on "join {workspace}",
     * not on create-your-own.
     *
     * <p>Carries <b>no token</b>. The emailed token only ever proved control of the invited mailbox,
     * and an authenticated user whose verified address matches the invitation has proven exactly that
     * — the token-less accept endpoint applies the same guards. (The anonymous preview endpoint already
     * exposes the same fields to any token holder; this is strictly less.)
     */
    private PendingInvitationSummary pendingInvitation(User user) {
        return invitations
                .findFirstByEmailAndStatusOrderByCreatedAtDesc(user.getEmail(), InvitationStatus.PENDING)
                .filter(invitation -> invitation.isRedeemable(Instant.now()))
                .flatMap(invitation -> workspaces.findById(invitation.getWorkspaceId())
                        .map(workspace -> new PendingInvitationSummary(
                                workspace.getName(), invitation.getRole().getName())))
                .orElse(null);
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
        List<WorkspaceRole> roles = membership.getRoles().stream()
                .map(Role::getName)
                .sorted(Comparator.naturalOrder())
                .map(WorkspaceRole::valueOf)
                .toList();

        return new WorkspaceSummary(
                workspace.getId(),
                workspace.getName(),
                workspace.getSlug(),
                workspace.getLogoMark(),
                workspace.getEmailDomain(),
                roles);
    }
}
