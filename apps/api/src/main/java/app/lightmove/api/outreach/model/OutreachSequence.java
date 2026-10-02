package app.lightmove.api.outreach.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A position's outreach sequence (V100): a name and up to three emails, saved whole by the editor. */
@Entity
@Table(name = "app_lm_outreach_sequence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutreachSequence extends BaseEntity {

    public static final int MAX_STEPS = 3;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_outreach_sequence_step", joinColumns = @JoinColumn(name = "sequence_id"))
    @OrderColumn(name = "sort_order")
    private List<SequenceStep> steps = new ArrayList<>();

    public static OutreachSequence written(UUID workspaceId, UUID projectId, UUID createdBy, String name,
                                           List<SequenceStep> steps) {
        OutreachSequence sequence = new OutreachSequence();
        sequence.workspaceId = workspaceId;
        sequence.projectId = projectId;
        sequence.createdBy = createdBy;
        sequence.rewrite(name, steps);
        return sequence;
    }

    public void rewrite(String newName, List<SequenceStep> newSteps) {
        this.name = newName;
        this.steps.clear();
        this.steps.addAll(newSteps);
    }

    public SequenceStep firstStep() {
        return steps.getFirst();
    }
}
