package app.lightmove.api.project.repository;

import app.lightmove.api.project.model.PendingRepresentativeAttachment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Parked attach intents for representatives who have not accepted yet. Rows are only ever reached
 * through a project or representative that was already resolved workspace-scoped — the ids here are
 * meaningless outside that resolution.
 */
public interface PendingRepresentativeAttachmentRepository
        extends JpaRepository<PendingRepresentativeAttachment, UUID> {

    /** The list-screen enrichment: pending contacts for a page of projects. */
    List<PendingRepresentativeAttachment> findByProjectIdIn(List<UUID> projectIds);

    /** Accept-time sweep: every mandate waiting on this representative. */
    List<PendingRepresentativeAttachment> findByRepresentativeId(UUID representativeId);

    boolean existsByProjectIdAndRepresentativeId(UUID projectId, UUID representativeId);

    long deleteByProjectIdAndRepresentativeId(UUID projectId, UUID representativeId);
}
