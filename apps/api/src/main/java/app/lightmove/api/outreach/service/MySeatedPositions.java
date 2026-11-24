package app.lightmove.api.outreach.service;

import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.model.ProjectMember;
import app.lightmove.api.project.repository.ProjectMemberRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.workspace.model.WorkspaceMember;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The live positions a member holds a seat on, newest first: Home's one definition of "live" — not archived, and not
 * delivered or closed ({@code ProjectStage.isDone}), as the SPA's Your positions reads it too.
 */
@Component
@RequiredArgsConstructor
public class MySeatedPositions {

    private final ProjectMemberRepository seats;
    private final ProjectRepository projects;

    public List<Project> liveOf(WorkspaceMember member) {
        Set<UUID> seated = seats.findByMemberId(member.getId()).stream()
                .map(ProjectMember::getProjectId).collect(Collectors.toSet());
        return projects.findAllById(seated).stream()
                .filter(project -> project.getWorkspaceId().equals(member.getWorkspaceId()) && !project.isArchived()
                        && !project.getStage().isDone())
                .sorted(Comparator.comparing(Project::getCreatedAt).reversed())
                .toList();
    }
}
