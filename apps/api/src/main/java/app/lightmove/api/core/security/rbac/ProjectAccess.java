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
 *   <li>the project exists in this workspace, else 404 (a foreign id confirms nothing) — checked
 *       <b>before</b> the admin bypass, so the guard stands on its own and a workspace admin is never
 *       authorised against a project outside their tenant;
 *   <li>the workspace-ADMIN bypass — a workspace admin is implicitly a project admin everywhere in
 *       their own workspace, so a departed mandate owner can never strand a search;
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

        // Scope the project to this workspace first, so the gate stands on its own — a workspace admin is
        // bypassed only for a project that is actually theirs, never for a foreign id they were never
        // entitled to name. (Service methods re-scope via requireProject too, but the gate must not
        // depend on that: a future endpoint that trusts the gate alone would otherwise act cross-tenant.)
        projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        if (workspaceAccess.isAdmin(member)) {
            return;
        }

        ProjectMember seat = seats.findByProjectIdAndMemberId(projectId, member.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "Not on this project's team"));

        if (!seats.findActionNames(seat.getId()).contains(action.name())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Requires the " + action.name() + " action");
        }
    }
}
