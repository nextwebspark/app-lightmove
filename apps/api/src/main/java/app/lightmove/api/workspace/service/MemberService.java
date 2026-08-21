package app.lightmove.api.workspace.service;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.RbacService;
import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The active roster: role changes and removals. Distinct from onboarding, which decides who gets in.
 *
 * <p>Tier gating (who may call this at all) lives on the controller as {@code @PreAuthorize} — the
 * guard beans re-read the database, so a revoked admin's stale token still gets refused. What stays
 * here are the invariants that need loaded state: the last-admin rule and the CLIENT exclusion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {

    private final WorkspaceMemberRepository members;
    private final WorkspaceAccess access;
    private final RbacService rbac;
    private final MemberDetachment detachment;
    private final AuditService audit;

    /**
     * Replace-set: the caller states the full set of workspace roles the member holds afterwards.
     * Self-demotion is allowed — under the same last-admin rule as everyone else.
     */
    @Transactional
    public WorkspaceMember changeRoles(UUID actorId, UUID workspaceId, UUID memberId,
                                       Set<WorkspaceRole> newRoles, HttpServletRequest request) {
        WorkspaceMember member = access.requireActiveMemberRow(memberId, workspaceId);

        // Clients are invited to a project, never granted through the roster. Groundwork guard: it
        // keeps the CLIENT role unreachable until the portal exists to receive one.
        if (newRoles.contains(WorkspaceRole.CLIENT)) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "Clients are invited to a project, not granted through the roster");
        }

        boolean isAdmin = holds(member, WorkspaceRole.ADMIN);
        if (isAdmin && !newRoles.contains(WorkspaceRole.ADMIN)) {
            requireAnotherAdmin(workspaceId);
        }

        String previous = roleNames(member);
        member.changeRoles(rbac.workspaceRoles(newRoles));

        audit.event(WorkspaceEventType.MEMBER_ROLE_CHANGED)
                .actor(actorId).workspace(workspaceId).target("member", memberId).from(request)
                .detail("from", previous)
                .detail("to", newRoles.stream().map(Enum::name).sorted().collect(Collectors.joining(",")))
                .record();

        return member;
    }

    /** Removal frees the one-active-membership index; self-removal is how someone leaves. */
    @Transactional
    public void remove(UUID actorId, UUID workspaceId, UUID memberId, HttpServletRequest request) {
        WorkspaceMember member = access.requireActiveMemberRow(memberId, workspaceId);

        if (holds(member, WorkspaceRole.ADMIN)) {
            requireAnotherAdmin(workspaceId);
        }
        detachment.assertRemovable(memberId);

        member.remove();
        detachment.detach(memberId);

        log.info("User {} removed member {} from workspace {}", actorId, memberId, workspaceId);
        audit.event(WorkspaceEventType.MEMBER_REMOVED)
                .actor(actorId).workspace(workspaceId).target("member", memberId).from(request)
                .detail("removedUserId", member.getUserId().toString())
                .record();
    }

    /**
     * Withdraws the CLIENT role a client-representative grant put on a membership — the mirror of
     * {@link InvitationService#onboardClientRepresentative}, called by the project side once the last
     * representative row behind that grant is revoked. A member who also holds a staff role keeps it.
     *
     * <p>When CLIENT was all they held, the <b>membership itself goes</b> rather than staying active with
     * an empty role set: {@code WorkspaceAccess.isPureClient} answers "false" for no roles at all, so a
     * role-less member would pass {@code requireStaff} and reach the roster and the client registry —
     * strictly more access than the portal guest they were. Removal also frees the one-active-membership
     * index, so the person can be invited again later.
     *
     * <p>No last-admin guard and no {@code assertRemovable}: a membership whose only role is CLIENT holds
     * neither the ADMIN role nor any staff project seat — {@code requireStaffRow} refuses to seat a pure
     * client in the first place.
     */
    @Transactional
    public void withdrawClientRole(UUID workspaceId, UUID userId, UUID actorId,
                                   HttpServletRequest request) {
        WorkspaceMember member = access.activeMember(userId, workspaceId).orElse(null);
        if (member == null) {
            return;
        }

        Set<Role> remaining = member.getRoles().stream()
                .filter(role -> !role.is(WorkspaceRole.CLIENT))
                .collect(Collectors.toSet());
        if (remaining.size() == member.getRoles().size()) {
            return;
        }

        String previous = roleNames(member);
        if (remaining.isEmpty()) {
            member.remove();
            detachment.detach(member.getId());

            log.info("Membership {} removed with the last client grant in workspace {}",
                    member.getId(), workspaceId);
            audit.event(WorkspaceEventType.MEMBER_REMOVED)
                    .actor(actorId).workspace(workspaceId).target("member", member.getId()).from(request)
                    .detail("removedUserId", userId.toString())
                    .detail("reason", "client_access_revoked")
                    .record();
            return;
        }

        member.changeRoles(remaining);
        audit.event(WorkspaceEventType.MEMBER_ROLE_CHANGED)
                .actor(actorId).workspace(workspaceId).target("member", member.getId()).from(request)
                .detail("from", previous)
                .detail("to", roleNames(member))
                .record();
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
