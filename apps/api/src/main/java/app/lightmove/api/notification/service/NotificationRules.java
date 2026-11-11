package app.lightmove.api.notification.service;

import app.lightmove.api.candidate.model.PersonNoteMentioned;
import app.lightmove.api.candidate.service.CandidateTaskService;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.tenant.WorkspaceScope;
import app.lightmove.api.notification.constant.NotificationKind;
import app.lightmove.api.notification.model.NotificationDraft;
import app.lightmove.api.project.model.ProjectSeatTaken;
import app.lightmove.api.task.model.OutreachFollowUp;
import app.lightmove.api.task.model.TaskAssigned;
import app.lightmove.api.task.model.TaskFallingDue;
import app.lightmove.api.task.model.TasksMovedToLead;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns what happened into rows of people's bells. Every recipient is read again before a row is written — still
 * staff, and still working the position the row is about — so nobody hears of a task or a note they could not
 * open. Each runs after the change commits, in a transaction of its own inside the event's workspace: a bell that fails
 * never undoes the work.
 */
@Component
@RequiredArgsConstructor
public class NotificationRules {

    private final NotificationWriter writer;
    private final WorkspaceAccess workspaceAccess;
    private final ProjectAccess projectAccess;
    private final CandidateTaskService people;
    private final PlatformTransactionManager transactionManager;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskAssigned(TaskAssigned assigned) {
        deliverInItsWorkspace(new NotificationDraft(assigned.workspaceId(), assigned.assigneeUserId(),
                NotificationKind.TASK_ASSIGNED, assigned.actorUserId(), assigned.projectId(), assigned.personId(),
                assigned.candidateId(), assigned.taskId(), assigned.noteId(), "task_assigned:" + assigned.taskId(), null,
                null, 1, null));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTasksMoved(TasksMovedToLead moved) {
        deliverInItsWorkspace(new NotificationDraft(moved.workspaceId(), moved.leadUserId(),
                NotificationKind.TASKS_MOVED, moved.fromUserId(), moved.projectId(), null, null, null, null,
                "tasks_moved:" + moved.projectId() + ":" + moved.fromUserId(), null, null, moved.count(), null));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskFallingDue(TaskFallingDue due) {
        NotificationKind kind = due.overdue() ? NotificationKind.TASK_OVERDUE : NotificationKind.TASK_DUE_SOON;
        deliverInItsWorkspace(new NotificationDraft(due.workspaceId(), due.assigneeUserId(), kind, null,
                due.projectId(), due.personId(), due.candidateId(), due.taskId(), null, kind.value() + ":" + due.taskId(),
                null, due.dueAt(), 1, null));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMentioned(PersonNoteMentioned mentioned) {
        inItsWorkspace(mentioned.workspaceId(), () -> {
            for (UUID recipient : mentioned.mentionedUserIds()) {
                deliver(new NotificationDraft(mentioned.workspaceId(), recipient, NotificationKind.MENTIONED,
                        mentioned.authorUserId(), mentioned.projectId(), mentioned.personId(), mentioned.candidateId(),
                        null, mentioned.noteId(), "mentioned:" + mentioned.noteId(), null, null, 1, null));
            }
        });
    }

    /** Replies and bounces on one position fold into one row, naming the latest people. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutreachFollowUp(OutreachFollowUp followUp) {
        inItsWorkspace(followUp.workspaceId(), () -> {
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
        });
    }

    /** Nobody is told they seated themselves. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatTaken(ProjectSeatTaken seated) {
        if (seated.userId().equals(seated.actorUserId())) {
            return;
        }
        deliverInItsWorkspace(new NotificationDraft(seated.workspaceId(), seated.userId(),
                NotificationKind.ADDED_TO_TEAM, seated.actorUserId(), seated.projectId(), null, null, null, null,
                "added_to_team:" + seated.projectId(), null, null, 1, null));
    }

    private void deliverInItsWorkspace(NotificationDraft draft) {
        inItsWorkspace(draft.workspaceId(), () -> deliver(draft));
    }

    /**
     * In the event's own workspace and a transaction of its own, never the submitter's: an account deletion moves
     * tasks in workspaces other than its session's, and by the time its listeners run that scope has closed — on a
     * pool thread, or on the deleting thread itself once it has committed, where a plain scope is refused.
     */
    private void inItsWorkspace(UUID workspaceId, Runnable delivery) {
        TransactionTemplate own = new TransactionTemplate(transactionManager);
        own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        WorkspaceScope.runInJoinedTransaction(workspaceId, () -> own.executeWithoutResult(status -> delivery.run()));
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
