package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.CandidateTagColour;
import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One of a workspace's own labels on its people (V98). Retired rather than deleted: the people who hold
 * it keep it, and their timeline still names it.
 */
@Entity
@Table(name = "app_lm_workspace_candidate_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandidateTag extends BaseEntity {

    public static final int MAX_LABEL = 40;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "label", nullable = false, length = MAX_LABEL)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "colour", nullable = false, length = 16)
    private CandidateTagColour colour;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    public static CandidateTag created(UUID workspaceId, String label, CandidateTagColour colour, UUID createdBy) {
        CandidateTag tag = new CandidateTag();
        tag.workspaceId = workspaceId;
        tag.label = label;
        tag.colour = colour;
        tag.createdBy = createdBy;
        return tag;
    }

    public void rename(String newLabel) {
        this.label = newLabel;
    }

    public void recolour(CandidateTagColour newColour) {
        this.colour = newColour;
    }

    public void retire() {
        if (retiredAt == null) {
            this.retiredAt = Instant.now();
        }
    }

    public void restore() {
        this.retiredAt = null;
    }

    public boolean isRetired() {
        return retiredAt != null;
    }
}
