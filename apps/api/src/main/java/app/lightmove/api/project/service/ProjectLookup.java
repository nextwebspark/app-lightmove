package app.lightmove.api.project.service;

import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ProjectRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;

/** How other modules read a position: the project module's repository stays its own. */
@Service
@RequiredArgsConstructor
@NamedInterface(name = "api", propagate = false)
public class ProjectLookup {

    private final ProjectRepository projects;

    /** The workspace's project, or a 404 — never a hint that the id exists in another tenant. */
    public Project requireInWorkspace(UUID projectId, UUID workspaceId) {
        return projects.requireInWorkspace(projectId, workspaceId);
    }

    /** Refuses whatever would start new work — outreach above all — on an archived position. */
    public void requireNotArchived(UUID projectId) {
        projects.requireNotArchived(projectId);
    }

    public boolean isArchived(UUID projectId) {
        return projects.existsByIdAndArchivedAtIsNotNull(projectId);
    }

    /** Of {@code projectIds}, the ones that are this workspace's projects. */
    public Set<UUID> findIdsInWorkspace(UUID workspaceId, Collection<UUID> projectIds) {
        return projects.findIdsInWorkspace(workspaceId, projectIds);
    }

    public Optional<Project> findById(UUID projectId) {
        return projects.findById(projectId);
    }

    public List<Project> findAllById(Iterable<UUID> projectIds) {
        return projects.findAllById(projectIds);
    }
}
