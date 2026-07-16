package app.lightmove.api.core.security.rbac;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.model.ProjectMember;
import app.lightmove.api.project.repository.ProjectMemberRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.workspace.model.WorkspaceMember;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The project-tier mirror of {@link WorkspaceAccess}: "may this user perform this action on this
 * project?" — re-read from the database on every check, for the same staleness reason.
 *
 * <p>The ladder, in order:
 *
 * <ol>
 *   <li>an active workspace membership, else 404 ({@code NOT_A_MEMBER} masking);
 *   <li>the workspace-ADMIN bypass — a workspace admin is implicitly a project admin everywhere, so a
 *       departed mandate owner can never strand a search;
 *   <li>the project must exist in this workspace, else 404 (a foreign id confirms nothing);
 *   <li>a seat on the team, else 403 — projects are browsable to staff, so existence is not a secret,
 *       but working one requires being on it;
 *   <li>the action, from the union of the seat's roles, else 403.
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ProjectAccess {

    private final WorkspaceAccess workspaceAccess;
    private final ProjectRepository projects;
    private final ProjectMemberRepository seats;

    public void requireAction(UUID userId, UUID workspaceId, UUID projectId, ProjectAction action) {
        WorkspaceMember member = workspaceAccess.requireActiveMember(userId, workspaceId);

        if (workspaceAccess.isAdmin(member)) {
            return;
        }

        projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        ProjectMember seat = seats.findByProjectIdAndMemberId(projectId, member.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "Not on this project's team"));

        if (!seats.findActionNames(seat.getId()).contains(action.name())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Requires the " + action.name() + " action");
        }
    }
}
