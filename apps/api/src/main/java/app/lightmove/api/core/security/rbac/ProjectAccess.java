package app.lightmove.api.core.security.rbac;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.model.ProjectMember;
import app.lightmove.api.project.repository.ProjectMemberRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.workspace.model.WorkspaceMember;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The project-tier mirror of {@link WorkspaceAccess}, re-read from the database every check. The
 * ladder: active membership, else 404; the project in this workspace, else 404 — <b>before</b> the
 * admin bypass, so an admin is never authorised outside their tenant; the workspace-ADMIN bypass; a
 * seat, else 403; the action from the seat's roles, else 403.
 */
@Service
@RequiredArgsConstructor
public class ProjectAccess {

    private final WorkspaceAccess workspaceAccess;
    private final ProjectRepository projects;
    private final ProjectMemberRepository seats;

    /**
     * Which of {@code projectIds} the caller holds {@code action} on — {@link #requireAction}'s ladder
     * asked once for many projects, for a screen that offers a control only where it would work. The
     * caller has already scoped the ids to the workspace; one outside it is simply never answered.
     */
    public Set<UUID> projectsWithAction(UUID userId, UUID workspaceId, Collection<UUID> projectIds,
                                        ProjectAction action) {
        if (projectIds.isEmpty()) {
            return Set.of();
        }
        WorkspaceMember member = workspaceAccess.requireActiveMember(userId, workspaceId);
        if (workspaceAccess.isAdmin(member)) {
            return projects.findIdsInWorkspace(workspaceId, projectIds);
        }
        return seats.findProjectIdsWithAction(member.getId(), projectIds, action.name());
    }

    /**
     * Staff only for a caller holding {@code WORK_EXECUTE} on the position; anyone else reads as a client
     * seat does, a project outside the workspace included.
     */
    public ProjectAudience audienceOf(UUID userId, UUID workspaceId, UUID projectId) {
        return projectsReadAsClient(userId, workspaceId, List.of(projectId)).isEmpty()
                ? ProjectAudience.STAFF : ProjectAudience.CLIENT;
    }

    /** {@link #audienceOf} for many projects at once: the ones the caller reads as a client seat. */
    public Set<UUID> projectsReadAsClient(UUID userId, UUID workspaceId, Collection<UUID> projectIds) {
        Set<UUID> worked = projectsWithAction(userId, workspaceId, projectIds, ProjectAction.WORK_EXECUTE);
        return projectIds.stream().filter(projectId -> !worked.contains(projectId)).collect(Collectors.toSet());
    }

    public void requireAction(UUID userId, UUID workspaceId, UUID projectId, ProjectAction action) {
        WorkspaceMember member = workspaceAccess.requireActiveMember(userId, workspaceId);

        // Scope the project to this workspace first, so the gate stands on its own — a workspace admin is
        // bypassed only for a project that is actually theirs, never for a foreign id they were never
        // entitled to name. (Services re-scope too, but the gate must not depend on that: a future
        // endpoint that trusts the gate alone would otherwise act cross-tenant.)
        projects.requireInWorkspace(projectId, workspaceId);

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
