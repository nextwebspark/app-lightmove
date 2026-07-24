package app.lightmove.api.project.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An attach decision made before the representative accepted their portal invitation. An INVITED
 * representative has no workspace membership to seat, so the intent is parked here and converted into a
 * real CLIENT project seat when they accept — then the row is deleted. Unique per
 * {@code (project, representative)}: re-attaching is a no-op, not a queue.
 */
@Entity
@Table(name = "app_lm_project_pending_representative")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingRepresentativeAttachment extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "representative_id", nullable = false)
    private UUID representativeId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    public static PendingRepresentativeAttachment of(UUID projectId, UUID representativeId,
                                                     UUID createdBy) {
        PendingRepresentativeAttachment attachment = new PendingRepresentativeAttachment();
        attachment.projectId = projectId;
        attachment.representativeId = representativeId;
        attachment.createdBy = createdBy;
        return attachment;
    }
}
