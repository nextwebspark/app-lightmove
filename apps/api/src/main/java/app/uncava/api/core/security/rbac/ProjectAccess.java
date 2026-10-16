package app.uncava.api.core.security.rbac;

import app.uncava.api.core.error.constant.ErrorCode;
import app.uncava.api.core.error.model.ApiException;
import app.uncava.api.project.model.ProjectMember;
import app.uncava.api.project.repository.ProjectMemberRepository;
import app.uncava.api.project.repository.ProjectRepository;
import app.uncava.api.workspace.constant.MemberStatus;
import app.uncava.api.workspace.model.WorkspaceMember;
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
 * admin bypass, so an admin is never authorised outside their tenant; the workspace-ADMIN bypass (every
 * action but archiving); a seat, else 403; the action from the seat's roles, else 403.
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
        if (workspaceAccess.isAdmin(member) && adminBypasses(action)) {
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

    /**
     * {@link #requireAction} as a question about someone other than the caller — may this person, named as an
     * assignee or in a mention, do {@code action} on the project now. Nobody outside the workspace may.
     */
    public boolean allows(UUID userId, UUID workspaceId, UUID projectId, ProjectAction action) {
        try {
            return !projectsWithAction(userId, workspaceId, List.of(projectId), action).isEmpty();
        } catch (ApiException notMember) {
            return false;
        }
    }

    /** The project's seated users whose seat holds {@code action}; a workspace admin with no seat is not listed. */
    public List<UUID> seatedUsersWithAction(UUID projectId, ProjectAction action) {
        return seats.findSeatedUserIdsWithAction(projectId, action.name(), MemberStatus.ACTIVE);
    }

    /** The project's leads, first seated first. */
    public List<UUID> leadsOf(UUID projectId) {
        return seats.findLeadUserIds(projectId, MemberStatus.ACTIVE);
    }

    /** Every action but {@link ProjectAction#PROJECT_ARCHIVE}: an admin seats themselves as lead to archive. */
    private static boolean adminBypasses(ProjectAction action) {
        return action != ProjectAction.PROJECT_ARCHIVE;
    }

    public void requireAction(UUID userId, UUID workspaceId, UUID projectId, ProjectAction action) {
        WorkspaceMember member = workspaceAccess.requireActiveMember(userId, workspaceId);

        // Scope the project to this workspace first, so the gate stands on its own — a workspace admin is
        // bypassed only for a project that is actually theirs, never for a foreign id they were never
        // entitled to name. (Services re-scope too, but the gate must not depend on that: a future
        // endpoint that trusts the gate alone would otherwise act cross-tenant.)
        projects.requireInWorkspace(projectId, workspaceId);

        if (workspaceAccess.isAdmin(member) && adminBypasses(action)) {
            return;
        }

        ProjectMember seat = seats.findByProjectIdAndMemberId(projectId, member.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "Not on this project's team"));

        if (!seats.findActionNames(seat.getId()).contains(action.name())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Requires the " + action.name() + " action");
        }
    }
}
