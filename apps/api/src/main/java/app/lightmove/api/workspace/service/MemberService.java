package app.lightmove.api.workspace.service;

import app.lightmove.api.core.audit.constant.AuditEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.constant.WorkspaceRole;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The active roster: role changes and removals. Distinct from onboarding, which decides who gets in.
 *
 * <p>A removed member's access token stays valid for up to 15 minutes; every admin-gated action
 * re-reads the membership row, so only stale reads survive until the next refresh fails.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {

    private final WorkspaceMemberRepository members;
    private final WorkspaceAccess access;
    private final MemberDetachment detachment;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<WorkspaceMember> activeMembers(UUID userId, UUID workspaceId) {
        access.requireActiveMember(userId, workspaceId);
        return members.findByWorkspaceIdAndStatus(workspaceId, MemberStatus.ACTIVE);
    }

    /** Self-demotion is allowed — under the same last-admin rule as everyone else. */
    @Transactional
    public WorkspaceMember changeRole(UUID actorId, UUID workspaceId, UUID memberId,
                                      WorkspaceRole newRole, HttpServletRequest request) {
        access.requireAdmin(actorId, workspaceId);
        WorkspaceMember member = access.requireActiveMemberRow(memberId, workspaceId);

        if (member.getRole() == WorkspaceRole.ADMIN && newRole != WorkspaceRole.ADMIN) {
            requireAnotherAdmin(workspaceId);
        }

        WorkspaceRole previous = member.getRole();
        member.changeRole(newRole);

        audit.event(AuditEventType.MEMBER_ROLE_CHANGED)
                .actor(actorId).workspace(workspaceId).target("member", memberId).from(request)
                .detail("from", previous.name()).detail("to", newRole.name())
                .record();

        return member;
    }

    /** Removal frees the one-active-membership index; self-removal is how someone leaves. */
    @Transactional
    public void remove(UUID actorId, UUID workspaceId, UUID memberId, HttpServletRequest request) {
        access.requireAdmin(actorId, workspaceId);
        WorkspaceMember member = access.requireActiveMemberRow(memberId, workspaceId);

        if (member.getRole() == WorkspaceRole.ADMIN) {
            requireAnotherAdmin(workspaceId);
        }
        detachment.assertRemovable(memberId);

        member.remove();
        detachment.detach(memberId);

        log.info("User {} removed member {} from workspace {}", actorId, memberId, workspaceId);
        audit.event(AuditEventType.MEMBER_REMOVED)
                .actor(actorId).workspace(workspaceId).target("member", memberId).from(request)
                .detail("removedUserId", member.getUserId().toString())
                .record();
    }

    private void requireAnotherAdmin(UUID workspaceId) {
        long admins = members.countByWorkspaceIdAndRoleAndStatus(
                workspaceId, WorkspaceRole.ADMIN, MemberStatus.ACTIVE);
        if (admins <= 1) {
            throw ApiException.of(ErrorCode.LAST_ADMIN);
        }
    }
}
