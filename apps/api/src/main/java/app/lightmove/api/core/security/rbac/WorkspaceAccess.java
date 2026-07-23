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
 * The single answer to "may this user act in this workspace?" — always re-read from the database,
 * never trusted from the JWT's roles claim, which may be up to 15 minutes stale. Membership misses are
 * served as {@link ErrorCode#NOT_A_MEMBER} (404) so probing an id confirms nothing.
 *
 * <p>Permission questions are asked per <b>action</b>, not per role: a member's effective permissions
 * are the union of their roles' actions, resolved through the seeded {@code app_lm_role_action}
 * mapping. Role names appear only where the role itself is the subject — the last-admin guard, the
 * CLIENT exclusion — never as a proxy for "may they do X".
 *
 * <p>Lives in {@code core/security/rbac} and reads a workspace repository — the same deliberate
 * core→feature seam CLAUDE.md documents for {@code AuthResponseAssembler}.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceAccess {

    private final WorkspaceMemberRepository members;

    public WorkspaceMember requireActiveMember(UUID userId, UUID workspaceId) {
        return members.findByWorkspaceIdAndUserIdAndStatus(workspaceId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_A_MEMBER));
    }

    /**
     * An active member who is <b>not a pure client</b>. Every staff-facing read gates on this rather than
     * mere membership, so a client representative can never see the roster, the registry or a mandate they
     * are not attached to. A CLIENT role held <i>alongside</i> a staff role does not fence anyone: a
     * colleague who also represents a client is still staff.
     */
    public WorkspaceMember requireStaff(UUID userId, UUID workspaceId) {
        WorkspaceMember member = requireActiveMember(userId, workspaceId);
        if (isPureClient(member.getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Client access is scoped to the projects you are on");
        }
        return member;
    }

    /** May this member perform this workspace action? The union of their roles decides. */
    public WorkspaceMember requireAction(UUID userId, UUID workspaceId, WorkspaceAction action) {
        WorkspaceMember member = requireActiveMember(userId, workspaceId);
        if (!members.findActionNames(member.getId()).contains(action.name())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Requires the " + action.name() + " action");
        }
        return member;
    }

    /**
     * Kept for the places where the ADMIN <i>role</i> itself is the subject — approving role changes and
     * the last-admin guard — not as a shortcut around {@link #requireAction}.
     */
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

    /** A membership row by its own id, scoped to the workspace — for sibling features naming a member. */
    public WorkspaceMember requireActiveMemberRow(UUID memberId, UUID workspaceId) {
        return members.findByIdAndWorkspaceId(memberId, workspaceId)
                .filter(WorkspaceMember::isActive)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_A_MEMBER));
    }

    /**
     * A membership row that belongs to <b>staff</b> — the by-id counterpart of {@link #requireStaff}.
     * A CLIENT (portal guest) is never seatable on a project team, so naming one here is refused; the
     * project's requested-role guard only covers the roles asked for, not the nature of the member.
     */
    public WorkspaceMember requireStaffRow(UUID memberId, UUID workspaceId) {
        WorkspaceMember member = requireActiveMemberRow(memberId, workspaceId);
        if (isPureClient(member.getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Client access is scoped to the projects you are on");
        }
        return member;
    }

    /**
     * A member whose <b>only</b> role is CLIENT — a client representative with no staff role. This, not
     * "holds CLIENT", is what fences someone out of staff surfaces: a member may hold CLIENT alongside a
     * staff role and is then treated as staff.
     */
    public boolean isPureClient(UUID memberId) {
        Set<String> roleNames = members.findRoleNames(memberId);
        return roleNames.size() == 1 && roleNames.contains(WorkspaceRole.CLIENT.name());
    }

    /** The active roster, for features that render members. Caller must already be authorised. */
    public List<WorkspaceMember> activeMembers(UUID workspaceId) {
        return members.findByWorkspaceIdAndStatus(workspaceId, MemberStatus.ACTIVE);
    }

    /**
     * The active <i>staff</i> roster — everyone in {@link #activeMembers} who is not a client
     * representative. The Team screen renders this, so a portal guest never appears among colleagues.
     */
    public List<WorkspaceMember> activeStaff(UUID workspaceId) {
        return members.findStaff(workspaceId, MemberStatus.ACTIVE, WorkspaceRole.CLIENT.name());
    }
}
