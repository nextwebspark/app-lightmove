package app.uncava.api.workspace.repository;

import app.uncava.api.workspace.model.Workspace;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    boolean existsBySlug(String slug);
}
