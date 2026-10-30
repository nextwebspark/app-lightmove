package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.CandidateSystemStatus;
import app.lightmove.api.candidate.constant.PipelineColour;
import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One status of a workspace's pipeline, always under exactly one {@link CandidateStage} (V131). A
 * candidate holds a status and nothing else: its stage, its rank and whether it has left the process are
 * all read through here. A retired status stays on whoever holds it and is never offered again.
 */
@Entity
@Table(name = "app_lm_workspace_candidate_status")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandidateStatusDefinition extends BaseEntity {

    public static final int MAX_LABEL = 40;

    /** Pipeline order: stage first, then the status within it. */
    public static final Comparator<CandidateStatusDefinition> PIPELINE_ORDER =
            Comparator.comparingInt((CandidateStatusDefinition status) -> status.getStage().getPosition())
                    .thenComparingInt(CandidateStatusDefinition::getPosition);

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_id", nullable = false)
    private CandidateStage stage;

    @Column(name = "key", nullable = false, updatable = false, length = 40)
    private String statusKey;

    @Column(name = "label", nullable = false, length = MAX_LABEL)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "colour", nullable = false, length = 16)
    private PipelineColour colour;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "is_default", nullable = false)
    private boolean defaultOfStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_key", updatable = false, length = 24)
    private CandidateSystemStatus systemKey;

    @Column(name = "out_of_process", nullable = false)
    private boolean outOfProcess;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    public static CandidateStatusDefinition added(CandidateStage stage, String statusKey, String label,
                                                  PipelineColour colour, int position, boolean defaultOfStage,
                                                  boolean outOfProcess, UUID createdBy) {
        CandidateStatusDefinition status = new CandidateStatusDefinition();
        status.workspaceId = stage.getWorkspaceId();
        status.stage = stage;
        status.statusKey = statusKey;
        status.label = label;
        status.colour = colour;
        status.position = position;
        status.defaultOfStage = defaultOfStage;
        status.outOfProcess = outOfProcess;
        status.createdBy = createdBy;
        return status;
    }

    public void rename(String newLabel) {
        this.label = newLabel;
    }

    public void recolour(PipelineColour newColour) {
        this.colour = newColour;
    }

    public void moveTo(CandidateStage newStage, int newPosition) {
        this.stage = newStage;
        this.position = newPosition;
        this.defaultOfStage = false;
    }

    public void markOutOfProcess(boolean out) {
        this.outOfProcess = out;
    }

    public void placeAt(int newPosition) {
        this.position = newPosition;
    }

    public void makeDefault() {
        this.defaultOfStage = true;
    }

    public void clearDefault() {
        this.defaultOfStage = false;
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

    public boolean is(CandidateSystemStatus system) {
        return systemKey == system;
    }

    /** Out of process: nobody at this status is approached again on that position, or moved on by an automation. */
    public boolean hasLeftTheRunning() {
        return outOfProcess;
    }

    /** Live and in process: somewhere a card can stand on the board. */
    public boolean isInProcess() {
        return !outOfProcess && !isRetired();
    }

    /** Whether this status stands before {@code other} on the pipeline. */
    public boolean isBefore(CandidateStatusDefinition other) {
        return PIPELINE_ORDER.compare(this, other) < 0;
    }
}
