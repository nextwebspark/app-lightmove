package app.lightmove.api.dataexport.repository;

import app.lightmove.api.dataexport.constant.WorkspaceExportStatus;
import app.lightmove.api.dataexport.model.WorkspaceExport;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceExportRepository extends JpaRepository<WorkspaceExport, UUID> {

    Optional<WorkspaceExport> findFirstByWorkspaceIdOrderByRequestedAtDesc(UUID workspaceId);

    Optional<WorkspaceExport> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<WorkspaceExport> findByWorkspaceIdAndStatus(UUID workspaceId, WorkspaceExportStatus status);
}
