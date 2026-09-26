package app.lightmove.api.core.security.controller;

import app.lightmove.api.core.security.dto.AuthResponse;
import app.lightmove.api.core.security.dto.PendingInvitationSummary;
import app.lightmove.api.core.security.dto.UserResponse;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.PlatformAccess;
import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.security.service.WorkspaceSelection;
import app.lightmove.api.core.security.token.TokenPair;
import app.lightmove.api.workspace.constant.InvitationStatus;
import app.lightmove.api.workspace.dto.WorkspaceCompanyResponse;
import app.lightmove.api.workspace.dto.WorkspaceSummary;
import app.lightmove.api.workspace.model.Invitation;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.InvitationRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final InvitationRepository invitations;
    private final UserRepository users;
    private final WorkspaceSelection selection;
    private final PlatformAccess platform;

    public AuthResponse assemble(TokenPair tokens, User user, WorkspaceMember membership) {
        return new AuthResponse(
                tokens.accessToken(),
                tokens.accessTokenTtl().toSeconds(),
                user(user, membership));
    }

    /**
     * @param membership the workspace this <i>session</i> is in, or null. Never "the user's workspace":
     *                   a user may be in several, and which one a token names is the session's business.
     */
    public UserResponse user(User user, WorkspaceMember membership) {
        WorkspaceSummary workspace = workspaceSummary(membership);
        List<WorkspaceMember> memberships = selection.all(user.getId());
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getTitle(),
                user.getAvatarUrl(),
                user.isEmailVerified(),
                user.hasPassword(),
                user.getTimezone(),
                user.getLocale(),
                workspace,
                workspaceSummaries(memberships),
                pendingInvitations(user, memberships),
                platform.actionsOf(user.getId()));
    }

    private List<WorkspaceSummary> workspaceSummaries(List<WorkspaceMember> memberships) {
        Map<UUID, Workspace> byId = workspaces
                .findAllById(memberships.stream().map(WorkspaceMember::getWorkspaceId).toList())
                .stream()
                .collect(Collectors.toMap(Workspace::getId, Function.identity()));
        return memberships.stream()
                .filter(membership -> byId.containsKey(membership.getWorkspaceId()))
                .map(membership -> toSummary(byId.get(membership.getWorkspaceId()), membership))
                .toList();
    }

    /**
     * The caller's own outstanding invitations, so routing can be derived from the server instead of a
     * tab's sessionStorage — an invitee who verifies in a fresh tab must land on "join {workspace}",
     * not on create-your-own — and so someone already placed learns of an invitation to a second
     * workspace without opening the email. One to a workspace they are already in is moot and hidden.
     *
     * <p>Carries <b>no token</b>. The emailed token only ever proved control of the invited mailbox,
     * and an authenticated user whose verified address matches the invitation has proven exactly that
     * — the accept-by-id endpoint applies the same guards. (The anonymous preview endpoint already
     * exposes the same fields to any token holder; this is strictly less.)
     */
    private List<PendingInvitationSummary> pendingInvitations(User user, List<WorkspaceMember> memberships) {
        Instant now = Instant.now();
        Set<UUID> alreadyIn = memberships.stream().map(WorkspaceMember::getWorkspaceId).collect(Collectors.toSet());
        List<Invitation> outstanding = invitations
                .findByEmailAndStatusOrderByCreatedAtDesc(user.getEmail(), InvitationStatus.PENDING)
                .stream()
                .filter(invitation -> invitation.isRedeemable(now))
                .filter(invitation -> !alreadyIn.contains(invitation.getWorkspaceId()))
                .toList();
        if (outstanding.isEmpty()) {
            return List.of();
        }

        Map<UUID, String> workspaceNames = workspaces
                .findAllById(outstanding.stream().map(Invitation::getWorkspaceId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Workspace::getId, Workspace::getName));
        Map<UUID, String> inviterNames = users
                .findAllById(outstanding.stream().map(Invitation::getInvitedBy).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return outstanding.stream()
                .filter(invitation -> workspaceNames.containsKey(invitation.getWorkspaceId()))
                .map(invitation -> new PendingInvitationSummary(
                        invitation.getId(),
                        workspaceNames.get(invitation.getWorkspaceId()),
                        invitation.getRole().getName(),
                        inviterNames.get(invitation.getInvitedBy())))
                .toList();
    }

    /** Null when the session is in no workspace — the user has not created or joined one yet. */
    private WorkspaceSummary workspaceSummary(WorkspaceMember membership) {
        if (membership == null || !membership.isActive()) {
            return null;
        }
        return workspaces.findById(membership.getWorkspaceId())
                .map(workspace -> toSummary(workspace, membership))
                .orElse(null);
    }

    /**
     * The workspace as its caller may see it.
     *
     * <p>A pure client is an outside contact at the hiring company, not a colleague: they get the
     * brand — name, slug, mark — and not {@code emailDomain}, which describes the firm's own people.
     * The roles are already in hand here, so this asks {@link WorkspaceRole#isStaff} rather than
     * re-reading the membership through {@code WorkspaceAccess}.
     */
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
                WorkspaceRole.isStaff(roles) ? workspace.getEmailDomain() : null,
                roles,
                membership.getJoinedAt(),
                WorkspaceCompanyResponse.of(workspace.getCompany()),
                workspace.getCompanySize(),
                workspace.getPrimaryRegion(),
                workspace.getTeamFocus());
    }
}
