package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.PipelineColour;
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
 * One column of a workspace's pipeline (V131). Retired rather than deleted, and only once none of its statuses is
 * live. Every stage is alike: whether someone has left the process is their status's own flag (V163).
 */
@Entity
@Table(name = "app_lm_workspace_candidate_stage")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandidateStage extends BaseEntity {

    public static final int MAX_LABEL = 40;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "key", nullable = false, updatable = false, length = 40)
    private String stageKey;

    @Column(name = "label", nullable = false, length = MAX_LABEL)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "colour", nullable = false, length = 16)
    private PipelineColour colour;

    @Column(name = "position", nullable = false)
    private int position;

    /** One of the six seeded stages' names; null for a stage a workspace added. */
    @Column(name = "system_key", updatable = false, length = 24)
    private String systemKey;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    public static CandidateStage added(UUID workspaceId, String stageKey, String label, PipelineColour colour,
                                       int position, UUID createdBy) {
        CandidateStage stage = new CandidateStage();
        stage.workspaceId = workspaceId;
        stage.stageKey = stageKey;
        stage.label = label;
        stage.colour = colour;
        stage.position = position;
        stage.createdBy = createdBy;
        return stage;
    }

    public void rename(String newLabel) {
        this.label = newLabel;
    }

    public void recolour(PipelineColour newColour) {
        this.colour = newColour;
    }

    public void placeAt(int newPosition) {
        this.position = newPosition;
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

    public boolean isSystem() {
        return systemKey != null;
    }
}
