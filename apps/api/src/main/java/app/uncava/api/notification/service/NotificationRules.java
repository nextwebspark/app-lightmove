package app.uncava.api.notification.service;

import app.uncava.api.candidate.model.PersonNoteMentioned;
import app.uncava.api.candidate.service.CandidateTaskService;
import app.uncava.api.core.security.rbac.ProjectAccess;
import app.uncava.api.core.security.rbac.ProjectAction;
import app.uncava.api.core.security.rbac.WorkspaceAccess;
import app.uncava.api.notification.constant.NotificationKind;
import app.uncava.api.notification.model.NotificationDraft;
import app.uncava.api.project.model.ProjectSeatTaken;
import app.uncava.api.task.model.OutreachFollowUp;
import app.uncava.api.task.model.TaskAssigned;
import app.uncava.api.task.model.TaskFallingDue;
import app.uncava.api.task.model.TasksMovedToLead;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns what happened into rows of people's bells. Every recipient is read again before a row is written — still
 * staff, and still working the position the row is about — so nobody hears of a task or a note they could not
 * open. Each runs after the change commits, in a transaction of its own: a bell that fails never undoes the work.
 */
@Component
@RequiredArgsConstructor
public class NotificationRules {

    private final NotificationWriter writer;
    private final WorkspaceAccess workspaceAccess;
    private final ProjectAccess projectAccess;
    private final CandidateTaskService people;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTaskAssigned(TaskAssigned assigned) {
        deliver(new NotificationDraft(assigned.workspaceId(), assigned.assigneeUserId(),
                NotificationKind.TASK_ASSIGNED, assigned.actorUserId(), assigned.projectId(), assigned.personId(),
                assigned.candidateId(), assigned.taskId(), assigned.noteId(), "task_assigned:" + assigned.taskId(), null,
                null, 1, null));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTasksMoved(TasksMovedToLead moved) {
        deliver(new NotificationDraft(moved.workspaceId(), moved.leadUserId(), NotificationKind.TASKS_MOVED,
                moved.fromUserId(), moved.projectId(), null, null, null, null,
                "tasks_moved:" + moved.projectId() + ":" + moved.fromUserId(), null, null, moved.count(), null));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTaskFallingDue(TaskFallingDue due) {
        NotificationKind kind = due.overdue() ? NotificationKind.TASK_OVERDUE : NotificationKind.TASK_DUE_SOON;
        deliver(new NotificationDraft(due.workspaceId(), due.assigneeUserId(), kind, null, due.projectId(),
                due.personId(), due.candidateId(), due.taskId(), null, kind.value() + ":" + due.taskId(), null,
                due.dueAt(), 1, null));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMentioned(PersonNoteMentioned mentioned) {
        for (UUID recipient : mentioned.mentionedUserIds()) {
            deliver(new NotificationDraft(mentioned.workspaceId(), recipient, NotificationKind.MENTIONED,
                    mentioned.authorUserId(), mentioned.projectId(), mentioned.personId(), mentioned.candidateId(),
                    null, mentioned.noteId(), "mentioned:" + mentioned.noteId(), null, null, 1, null));
        }
    }

    /** Replies and bounces on one position fold into one row, naming the latest people. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOutreachFollowUp(OutreachFollowUp followUp) {
        String name = people.personNamesOf(followUp.workspaceId(), List.of(followUp.personId()))
                .get(followUp.personId());
        NotificationKind kind = switch (followUp.kind()) {
            case REPLIED -> NotificationKind.OUTREACH_REPLIED;
            case BOUNCED -> NotificationKind.OUTREACH_BOUNCED;
            case CALL_BOOKED -> NotificationKind.CALL_BOOKED;
        };
        String groupKey = kind == NotificationKind.CALL_BOOKED
                ? kind.value() + ":" + followUp.projectId() + ":" + followUp.personId() + ":" + followUp.dueAt()
                : kind.value() + ":" + followUp.projectId();
        deliver(new NotificationDraft(followUp.workspaceId(), followUp.recipientUserId(), kind, null,
                followUp.projectId(), followUp.personId(), followUp.candidateId(), followUp.taskId(), null, groupKey,
                name, kind == NotificationKind.CALL_BOOKED ? followUp.dueAt() : null, 1, null));
    }

    /** Nobody is told they seated themselves. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSeatTaken(ProjectSeatTaken seated) {
        if (seated.userId().equals(seated.actorUserId())) {
            return;
        }
        deliver(new NotificationDraft(seated.workspaceId(), seated.userId(), NotificationKind.ADDED_TO_TEAM,
                seated.actorUserId(), seated.projectId(), null, null, null, null,
                "added_to_team:" + seated.projectId(), null, null, 1, null));
    }

    private void deliver(NotificationDraft draft) {
        if (!workspaceAccess.isStaff(draft.recipientUserId(), draft.workspaceId())) {
            return;
        }
        if (draft.projectId() != null && !projectAccess.allows(draft.recipientUserId(), draft.workspaceId(),
                draft.projectId(), ProjectAction.WORK_EXECUTE)) {
            return;
        }
        writer.write(draft);
    }
}
