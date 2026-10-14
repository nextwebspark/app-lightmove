package app.lightmove.api.task.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.task.constant.TaskKind;
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
 * One of a workspace's next-action presets (V133). Retired rather than deleted: a task filed from it keeps
 * pointing at it, and it is simply no longer offered.
 */
@Entity
@Table(name = "app_lm_workspace_task_preset")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskPreset extends BaseEntity {

    public static final int MAX_LABEL = 80;
    public static final int MAX_DUE_OFFSET_DAYS = 365;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "label", nullable = false, length = MAX_LABEL)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private TaskKind kind;

    /** Days from today the due date defaults to; 0 is the same day, null leaves it to the person. */
    @Column(name = "due_offset_days")
    private Integer dueOffsetDays;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    public static TaskPreset created(UUID workspaceId, String label, TaskKind kind, Integer dueOffsetDays,
                                     int position, UUID createdBy) {
        TaskPreset preset = new TaskPreset();
        preset.workspaceId = workspaceId;
        preset.label = label;
        preset.kind = kind;
        preset.dueOffsetDays = dueOffsetDays;
        preset.position = position;
        preset.createdBy = createdBy;
        return preset;
    }

    public void rename(String newLabel) {
        this.label = newLabel;
    }

    public void rekind(TaskKind newKind) {
        this.kind = newKind;
    }

    public void defaultDueIn(Integer newDueOffsetDays) {
        this.dueOffsetDays = newDueOffsetDays;
    }

    public void moveTo(int newPosition) {
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
}
