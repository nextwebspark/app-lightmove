package app.lightmove.api.outreach.service;

import app.lightmove.api.core.tenant.WorkspaceScope;
import app.lightmove.api.outreach.constant.EnrollmentStatus;
import app.lightmove.api.outreach.constant.MessageStatus;
import app.lightmove.api.outreach.constant.OutreachStopReason;
import app.lightmove.api.outreach.repository.OutreachEnrollmentRepository;
import app.lightmove.api.outreach.repository.OutreachMessageRepository;
import app.lightmove.api.workspace.model.WorkspaceDeleted;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * A deleted workspace sends nothing more: every live run stops and every email still waiting is cancelled in the
 * deleting transaction, as an archived position's are. Once it commits, every mailbox and Zoom account connected
 * there is let go at its provider, off the request thread, so no token the workspace held outlives its members' access.
 */
@Component
@RequiredArgsConstructor
class WorkspaceDeletionOutreach {

    private final OutreachEnrollmentRepository enrollments;
    private final OutreachOutcomes outcomes;
    private final OutreachMessageRepository messages;
    private final OutreachAttachments attachments;
    private final MailboxService mailboxes;
    private final ZoomService zoom;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onDeleted(WorkspaceDeleted deleted) {
        enrollments.findByWorkspaceIdAndStatusIn(deleted.workspaceId(), EnrollmentStatus.LIVE)
                .forEach(enrollment -> outcomes.stop(enrollment, OutreachStopReason.WORKSPACE_DELETED,
                        deleted.actorUserId(), deleted.deletedAt(), null));
        // One the dispatcher has already taken is on its way; its next re-check finds the mailbox gone.
        messages.findByWorkspaceIdAndStatus(deleted.workspaceId(), MessageStatus.SCHEDULED).stream()
                .filter(message -> message.getSendingSince() == null)
                .forEach(message -> {
                    message.cancel();
                    attachments.deleteFilesOnCommit(message.getId());
                });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onDeletionCommitted(WorkspaceDeleted deleted) {
        WorkspaceScope.run(deleted.workspaceId(), () -> {
            mailboxes.disconnectAllOf(deleted.workspaceId(), deleted.actorUserId());
            zoom.disconnectAllOf(deleted.workspaceId(), deleted.actorUserId());
        });
    }
}
