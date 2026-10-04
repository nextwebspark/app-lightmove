package app.lightmove.api.project.repository;

import app.lightmove.api.project.model.PendingRepresentativeAttachment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Only ever reached through a project or representative already resolved workspace-scoped. */
public interface PendingRepresentativeAttachmentRepository
        extends JpaRepository<PendingRepresentativeAttachment, UUID> {

    List<PendingRepresentativeAttachment> findByProjectIdIn(List<UUID> projectIds);

    List<PendingRepresentativeAttachment> findByRepresentativeId(UUID representativeId);

    boolean existsByProjectIdAndRepresentativeId(UUID projectId, UUID representativeId);

    long deleteByProjectIdAndRepresentativeId(UUID projectId, UUID representativeId);
}
