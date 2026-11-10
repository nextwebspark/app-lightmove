package app.lightmove.api.workspace.service;

import app.lightmove.api.billing.seat.service.SeatAllowance;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.apikey.ApiKeyService;
import app.lightmove.api.core.security.oauth.OAuthGrantService;
import app.lightmove.api.core.security.rbac.RbacService;
import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.workspace.constant.InvitationWithdrawal;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.model.MemberAccessNarrowed;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Roster role changes and removals. Gating is the controller's {@code @PreAuthorize}; the invariants
 * needing loaded state stay here: the last-admin rule and the CLIENT exclusion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {

    private final WorkspaceMemberRepository members;
    private final WorkspaceRepository workspaces;
    private final WorkspaceAccess access;
    private final RbacService rbac;
    private final MemberDetachment detachment;
    private final ApiKeyService apiKeys;
    private final OAuthGrantService oauthGrants;
    private final SeatAllowance seats;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final InvitationService invitations;

    /**
     * Replaces the member's staff roles; a CLIENT role they hold as a client contact is kept, since the roster can
     * neither grant nor take it. Self-demotion is allowed under the same last-admin rule.
     */
    @Transactional
    public WorkspaceMember changeRoles(UUID actorId, UUID workspaceId, UUID memberId,
                                       Set<WorkspaceRole> newRoles, HttpServletRequest request) {
        lockRoster(workspaceId);
        WorkspaceMember member = access.requireActiveMemberRow(memberId, workspaceId);

        // CLIENT comes from a project invitation, never the roster.
        if (newRoles.contains(WorkspaceRole.CLIENT)) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "Representatives are invited to a position, not granted through the roster");
        }

        boolean isAdmin = holds(member, WorkspaceRole.ADMIN);
        boolean dropsAdmin = isAdmin && !newRoles.contains(WorkspaceRole.ADMIN);
        if (dropsAdmin) {
            requireAnotherAdmin(workspaceId);
        }
        boolean wasStaff = member.holdsStaffRole();
        boolean staffAfter = !newRoles.isEmpty();
        if (staffAfter && !wasStaff) {
            seats.requireRoomFor(workspaceId, 1);
        }

        String previous = roleNames(member);
        // A replaced set dropped the CLIENT role of someone both staff and a client contact.
        Set<Role> next = new HashSet<>(rbac.workspaceRoles(newRoles));
        member.getRoles().stream().filter(role -> role.is(WorkspaceRole.CLIENT)).forEach(next::add);
        member.changeRoles(next);
        if (staffAfter != wasStaff) {
            seats.onStaffSeatsChanged(workspaceId);
        }
        if (dropsAdmin) {
            events.publishEvent(new MemberAccessNarrowed(workspaceId, member.getUserId(), actorId));
            // Their staff invitations were an admin's approval, which no longer stands.
            invitations.withdrawSentBy(actorId, workspaceId, member.getUserId(), InvitationWithdrawal.INVITER_DEMOTED,
                    request);
        }

        audit.event(WorkspaceEventType.MEMBER_ROLE_CHANGED)
                .actor(actorId).workspace(workspaceId).target("member", memberId).from(request)
                .detail("from", previous)
                .detail("to", roleNames(member))
                .record();

        return member;
    }

    /** Self-removal is how someone leaves; their session moves to another workspace at its next refresh. */
    @Transactional
    public void remove(UUID actorId, UUID workspaceId, UUID memberId, HttpServletRequest request) {
        lockRoster(workspaceId);
        WorkspaceMember member = access.requireActiveMemberRow(memberId, workspaceId);

        if (holds(member, WorkspaceRole.ADMIN)) {
            requireAnotherAdmin(workspaceId);
        }
        detachment.assertRemovable(memberId);

        member.remove();
        detachment.detach(memberId);
        events.publishEvent(new MemberAccessNarrowed(workspaceId, member.getUserId(), actorId));
        if (member.holdsStaffRole()) {
            seats.onStaffSeatsChanged(workspaceId);
        }
        apiKeys.revokeOnMembershipEnd(actorId, workspaceId, member.getUserId(), request);
        invitations.withdrawSentBy(actorId, workspaceId, member.getUserId(), InvitationWithdrawal.INVITER_REMOVED,
                request);
        oauthGrants.revokeOnMembershipEnd(actorId, workspaceId, member.getUserId(), request);

        log.info("User {} removed member {} from workspace {}", actorId, memberId, workspaceId);
        audit.event(WorkspaceEventType.MEMBER_REMOVED)
                .actor(actorId).workspace(workspaceId).target("member", memberId).from(request)
                .detail("removedUserId", member.getUserId().toString())
                .record();
    }

    /**
     * Taken before the member row is read: the last-admin count is a read, and two admins demoting or removing each
     * other at once each counted the other and left the workspace with no admin.
     */
    private void lockRoster(UUID workspaceId) {
        workspaces.lockForRosterChange(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private boolean holds(WorkspaceMember member, WorkspaceRole role) {
        return member.getRoles().stream().anyMatch(r -> r.is(role));
    }

    private String roleNames(WorkspaceMember member) {
        return member.getRoles().stream().map(Role::getName).sorted().collect(Collectors.joining(","));
    }

    private void requireAnotherAdmin(UUID workspaceId) {
        long admins = members.countByRoleName(
                workspaceId, WorkspaceRole.ADMIN.name(), MemberStatus.ACTIVE);
        if (admins <= 1) {
            throw ApiException.of(ErrorCode.LAST_ADMIN);
        }
    }
}
