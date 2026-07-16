package app.lightmove.api.workspace.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.constant.WorkspaceRole;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The single answer to "may this user act in this workspace?" — always re-read from the database,
 * never trusted from the JWT's role claim, which may be up to 15 minutes stale. Membership misses are
 * served as {@link ErrorCode#NOT_A_MEMBER} (404) so probing an id confirms nothing.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceAccess {

    private final WorkspaceMemberRepository members;

    public WorkspaceMember requireActiveMember(UUID userId, UUID workspaceId) {
        return members.findByWorkspaceIdAndUserIdAndStatus(workspaceId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_A_MEMBER));
    }

    public WorkspaceMember requireAdmin(UUID userId, UUID workspaceId) {
        WorkspaceMember member = requireActiveMember(userId, workspaceId);
        if (member.getRole() != WorkspaceRole.ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Requires the ADMIN role");
        }
        return member;
    }

    /** A membership row by its own id, scoped to the workspace — for sibling features naming a member. */
    public WorkspaceMember requireActiveMemberRow(UUID memberId, UUID workspaceId) {
        return members.findByIdAndWorkspaceId(memberId, workspaceId)
                .filter(WorkspaceMember::isActive)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_A_MEMBER));
    }

    /** The active roster, for features that render members. Caller must already be authorised. */
    public List<WorkspaceMember> activeMembers(UUID workspaceId) {
        return members.findByWorkspaceIdAndStatus(workspaceId, MemberStatus.ACTIVE);
    }
}
