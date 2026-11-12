package app.lightmove.api.core.security.controller;

import app.lightmove.api.common.time.model.DateTimePreferences;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.dto.AuthResponse;
import app.lightmove.api.core.security.dto.DateTimeChoiceResponse;
import app.lightmove.api.core.security.dto.PendingInvitationSummary;
import app.lightmove.api.core.security.dto.UserDateTimeResponse;
import app.lightmove.api.core.security.dto.UserResponse;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.PlatformAccess;
import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.security.service.WorkspaceSelection;
import app.lightmove.api.core.security.token.TokenPair;
import app.lightmove.api.core.tenant.ResolvedRow;
import app.lightmove.api.core.tenant.TenantResolvers;
import app.lightmove.api.core.tenant.WorkspaceScope;
import app.lightmove.api.workspace.dto.WorkspaceCompanyResponse;
import app.lightmove.api.workspace.dto.WorkspaceSummary;
import app.lightmove.api.workspace.model.Invitation;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.InvitationRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import app.lightmove.api.workspace.service.DateTimePreferenceService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    private final DateTimePreferenceService dateTimePreferences;
    private final TenantResolvers resolvers;

    public AuthResponse assemble(TokenPair tokens, User user, WorkspaceMember membership) {
        return new AuthResponse(
                tokens.accessToken(),
                tokens.accessTokenTtl().toSeconds(),
                user(user, membership));
    }

    /** @param membership the workspace this <i>session</i> is in, or null — never "the user's", of several */
    public UserResponse user(User user, WorkspaceMember membership) {
        List<WorkspaceMember> memberships = selection.all(user.getId());
        Map<UUID, Workspace> byId = workspacesOf(memberships);
        Workspace current = membership == null || !membership.isActive() ? null : byId.get(membership.getWorkspaceId());
        DateTimePreferences effective = dateTimePreferences.forPerson(user, current);
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getTitle(),
                user.getAvatarUrl(),
                user.isAvatarEditable(),
                user.getPhone(),
                user.getLinkedinUrl(),
                user.isEmailVerified(),
                user.hasPassword(),
                effective.zone().getId(),
                new UserDateTimeResponse(
                        DateTimeChoiceResponse.of(effective),
                        new DateTimeChoiceResponse(user.getTimezone(), user.getDateFormat(), user.getTimeFormat()),
                        current == null ? null : DateTimeChoiceResponse.of(dateTimePreferences.forWorkspace(current))),
                user.getLocale(),
                currentSummary(membership, byId),
                memberships.stream()
                        .filter(member -> byId.containsKey(member.getWorkspaceId()))
                        .map(member -> toSummary(byId.get(member.getWorkspaceId()), member))
                        .toList(),
                pendingInvitations(user, memberships),
                platform.actionsOf(user.getId()));
    }

    /** As a member of exactly this workspace, or none — the session's, or one just created or joined. */
    public UserResponse userIn(UUID userId, UUID workspaceId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));
        return user(user, selection.membershipIn(userId, workspaceId).orElse(null));
    }

    private Map<UUID, Workspace> workspacesOf(List<WorkspaceMember> memberships) {
        return workspaces
                .findAllById(memberships.stream().map(WorkspaceMember::getWorkspaceId).toList())
                .stream()
                .collect(Collectors.toMap(Workspace::getId, Function.identity()));
    }

    /**
     * The caller's outstanding invitations, so routing comes from the server, not sessionStorage; one to a current
     * workspace is hidden only when {@link WorkspaceMember#alreadyCovers} it. Carries no token (a verified address
     * proves the same mailbox). None for an unverified account, which would leak which firms invited the address.
     */
    private List<PendingInvitationSummary> pendingInvitations(User user, List<WorkspaceMember> memberships) {
        if (!user.isEmailVerified()) {
            return List.of();
        }
        Instant now = Instant.now();
        Map<UUID, WorkspaceMember> membershipByWorkspace = memberships.stream()
                .collect(Collectors.toMap(WorkspaceMember::getWorkspaceId, Function.identity()));
        // Each from its own workspace: the invitations to a workspace the person is not in yet are that workspace's
        // rows, which the session's own workspace cannot read.
        Map<UUID, List<UUID>> waitingIn = resolvers.pendingInvitationsFor(user.getEmail()).stream()
                .collect(Collectors.groupingBy(ResolvedRow::workspaceId,
                        Collectors.mapping(ResolvedRow::id, Collectors.toList())));
        List<Invitation> outstanding = waitingIn.entrySet().stream()
                .flatMap(waiting -> WorkspaceScope.call(waiting.getKey(),
                        () -> invitations.findAllById(waiting.getValue())).stream())
                .filter(invitation -> invitation.isRedeemable(now))
                .filter(invitation -> {
                    WorkspaceMember member = membershipByWorkspace.get(invitation.getWorkspaceId());
                    return member == null || !member.alreadyCovers(invitation.getRole());
                })
                .sorted(Comparator.comparing(Invitation::getCreatedAt).reversed())
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
                        invitation.getWorkspaceId(),
                        workspaceNames.get(invitation.getWorkspaceId()),
                        invitation.getRole().getName(),
                        inviterNames.get(invitation.getInvitedBy())))
                .toList();
    }

    private static WorkspaceSummary currentSummary(WorkspaceMember membership, Map<UUID, Workspace> byId) {
        if (membership == null || !membership.isActive() || !byId.containsKey(membership.getWorkspaceId())) {
            return null;
        }
        return toSummary(byId.get(membership.getWorkspaceId()), membership);
    }

    /**
     * The workspace as its caller may see it: a pure client gets the brand (name, slug, mark), never
     * {@code emailDomain}, which describes the firm's own people.
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
                workspace.getMode(),
                WorkspaceRole.isStaff(roles) ? workspace.getEmailDomain() : null,
                roles,
                membership.getJoinedAt(),
                WorkspaceCompanyResponse.of(workspace.getCompany()));
    }
}
