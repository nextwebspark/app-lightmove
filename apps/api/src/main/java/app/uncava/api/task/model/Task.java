package app.uncava.api.task.model;

import app.uncava.api.core.persistence.model.BaseEntity;
import app.uncava.api.task.constant.TaskKind;
import app.uncava.api.task.constant.TaskRule;
import app.uncava.api.task.constant.TaskSource;
import app.uncava.api.task.constant.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One job on one executive of one position (V133). The person is kept beside the mapping, so an executive
 * unmapped from the position keeps their task history; the position's own reads go through the mapping.
 */
@Entity
@Table(name = "app_lm_task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task extends BaseEntity {

    public static final int MAX_TITLE = 200;
    /** A title is one line: it becomes an email subject. */
    public static final String ONE_LINE = "[^\\r\\n]*";
    public static final String ONE_LINE_ONLY = "Keep it to one line";
    public static final int MAX_DETAIL = 500;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "person_id", nullable = false, updatable = false)
    private UUID personId;

    @Column(name = "candidate_id")
    private UUID candidateId;

    @Column(name = "note_id")
    private UUID noteId;

    @Column(name = "preset_id")
    private UUID presetId;

    @Column(name = "title", nullable = false, length = MAX_TITLE)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private TaskKind kind;

    @Column(name = "detail", length = MAX_DETAIL)
    private String detail;

    @Column(name = "assignee_user_id", nullable = false)
    private UUID assigneeUserId;

    @Column(name = "due_at")
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16, updatable = false)
    private TaskSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule", length = 16, updatable = false)
    private TaskRule rule;

    @Column(name = "rule_key", length = 160, updatable = false)
    private String ruleKey;

    /** Null when a rule filed it. */
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "due_soon_notified_at")
    private Instant dueSoonNotifiedAt;

    @Column(name = "overdue_notified_at")
    private Instant overdueNotifiedAt;

    public static Task planned(TaskPlan plan, UUID createdBy) {
        Task task = from(plan);
        task.source = plan.noteId() == null ? TaskSource.MANUAL : TaskSource.NOTE;
        task.createdBy = createdBy;
        return task;
    }

    public static Task filedByRule(TaskPlan plan, TaskRule rule, String ruleKey) {
        Task task = from(plan);
        task.source = TaskSource.RULE;
        task.rule = rule;
        task.ruleKey = ruleKey;
        return task;
    }

    private static Task from(TaskPlan plan) {
        Task task = new Task();
        task.workspaceId = plan.workspaceId();
        task.projectId = plan.projectId();
        task.personId = plan.personId();
        task.candidateId = plan.candidateId();
        task.noteId = plan.noteId();
        task.presetId = plan.presetId();
        task.title = plan.title();
        task.kind = plan.kind();
        task.detail = plan.detail();
        task.assigneeUserId = plan.assigneeUserId();
        task.dueAt = plan.dueAt();
        task.status = TaskStatus.OPEN;
        return task;
    }

    public void retitle(String newTitle, UUID newPresetId) {
        this.title = newTitle;
        this.presetId = newPresetId;
    }

    public void rekind(TaskKind newKind) {
        this.kind = newKind;
    }

    public void describe(String newDetail) {
        this.detail = newDetail;
    }

    /** A moved due date is news again: both notices may go once more. */
    public void reschedule(Instant newDueAt) {
        if (!Objects.equals(dueAt, newDueAt)) {
            this.dueAt = newDueAt;
            this.dueSoonNotifiedAt = null;
            this.overdueNotifiedAt = null;
        }
    }

    /** Whether it changed hands. */
    public boolean reassign(UUID newAssignee) {
        boolean changed = !assigneeUserId.equals(newAssignee);
        this.assigneeUserId = newAssignee;
        return changed;
    }

    public void complete(UUID by, Instant at) {
        this.status = TaskStatus.DONE;
        this.completedBy = by;
        this.completedAt = at;
    }

    public void reopen() {
        this.status = TaskStatus.OPEN;
        this.completedBy = null;
        this.completedAt = null;
    }

    public void cancel() {
        this.status = TaskStatus.CANCELLED;
        this.completedBy = null;
        this.completedAt = null;
    }

    public boolean isOpen() {
        return status == TaskStatus.OPEN;
    }
}
