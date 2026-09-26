package app.lightmove.api.core.security.rbac;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * "May this user act in this workspace?" — re-read from the database, never the JWT's possibly stale
 * roles claim, and asked per action; a miss is {@link ErrorCode#NOT_A_MEMBER} (404) so probing confirms nothing.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceAccess {

    private final WorkspaceMemberRepository members;

    public WorkspaceMember requireActiveMember(UUID userId, UUID workspaceId) {
        return members.findByWorkspaceIdAndUserIdAndStatus(workspaceId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_A_MEMBER));
    }

    /** An active member who is not a pure client; every staff-facing read gates on this, not membership. */
    public WorkspaceMember requireStaff(UUID userId, UUID workspaceId) {
        WorkspaceMember member = requireActiveMember(userId, workspaceId);
        if (isPureClient(member.getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Client access is scoped to the projects you are on");
        }
        return member;
    }

    /** The union of the member's roles decides. */
    public WorkspaceMember requireAction(UUID userId, UUID workspaceId, WorkspaceAction action) {
        WorkspaceMember member = requireActiveMember(userId, workspaceId);
        if (!members.findActionNames(member.getId()).contains(action.name())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Requires the " + action.name() + " action");
        }
        return member;
    }

    /** Only where the ADMIN role itself is the subject, never a shortcut around {@link #requireAction}. */
    public WorkspaceMember requireAdmin(UUID userId, UUID workspaceId) {
        WorkspaceMember member = requireActiveMember(userId, workspaceId);
        if (!isAdmin(member)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Requires the ADMIN role");
        }
        return member;
    }

    public boolean isAdmin(WorkspaceMember member) {
        return members.findRoleNames(member.getId()).contains(WorkspaceRole.ADMIN.name());
    }

    public WorkspaceMember requireActiveMemberRow(UUID memberId, UUID workspaceId) {
        return members.findByIdAndWorkspaceId(memberId, workspaceId)
                .filter(WorkspaceMember::isActive)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_A_MEMBER));
    }

    /** By-id {@link #requireStaff}: the project's requested-role guard does not stop a pure client being seated. */
    public WorkspaceMember requireStaffRow(UUID memberId, UUID workspaceId) {
        WorkspaceMember member = requireActiveMemberRow(memberId, workspaceId);
        if (isPureClient(member.getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Client access is scoped to the projects you are on");
        }
        return member;
    }

    /** Only CLIENT — not "holds CLIENT" — fences someone out of staff surfaces. */
    public boolean isPureClient(UUID memberId) {
        Set<String> roleNames = members.findRoleNames(memberId);
        return roleNames.size() == 1 && roleNames.contains(WorkspaceRole.CLIENT.name());
    }

    /** Caller must already be authorised. */
    public List<WorkspaceMember> activeMembers(UUID workspaceId) {
        return members.findByWorkspaceIdAndStatus(workspaceId, MemberStatus.ACTIVE);
    }

    public List<WorkspaceMember> activeStaff(UUID workspaceId) {
        return members.findStaff(workspaceId, MemberStatus.ACTIVE, WorkspaceRole.CLIENT.name());
    }
}
