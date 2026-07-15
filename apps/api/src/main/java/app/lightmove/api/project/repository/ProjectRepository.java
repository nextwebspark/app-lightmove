package app.lightmove.api.project.repository;

import app.lightmove.api.project.model.Project;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every finder carries the workspace id — an unscoped lookup on tenant data must not exist. */
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<Project> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<Project> findByWorkspaceIdAndClientId(UUID workspaceId, UUID clientId);
}
