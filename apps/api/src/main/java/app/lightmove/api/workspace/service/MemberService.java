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
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
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
