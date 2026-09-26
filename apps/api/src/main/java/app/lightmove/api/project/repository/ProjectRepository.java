package app.lightmove.api.project.repository;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.model.Project;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every finder carries the workspace id — an unscoped lookup on tenant data must not exist. */
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<Project> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    /** The workspace's project, or a 404 — never a hint that the id exists in another tenant. */
    default Project requireInWorkspace(UUID id, UUID workspaceId) {
        return findByIdAndWorkspaceId(id, workspaceId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    List<Project> findByWorkspaceIdAndClientIdOrderByCreatedAtDesc(UUID workspaceId, UUID clientId);
}
