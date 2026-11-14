package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.constant.PrivacyRequestKind;
import app.lightmove.api.candidate.model.PersonOptedOut;
import app.lightmove.api.candidate.model.PersonPrivacyRequested;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.outreach.constant.EnrollmentStatus;
import app.lightmove.api.outreach.constant.MessageStatus;
import app.lightmove.api.outreach.constant.OutreachStopReason;
import app.lightmove.api.outreach.repository.OutreachEnrollmentRepository;
import app.lightmove.api.outreach.repository.OutreachMessageRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A person who stopped emails (#924), or objected from their privacy page (#923), is sent nothing more: every live run
 * on them stops as opted out, and every email a consultant scheduled for them that the dispatcher has not yet taken
 * fails, in the transaction that recorded it. One already taken is on its way; its send-time re-check finds do not
 * contact.
 */
@Component
@RequiredArgsConstructor
class OutreachOptOutStop {

    private final OutreachEnrollmentRepository enrollments;
    private final OutreachOutcomes outcomes;
    private final OutreachMessageRepository messages;
    private final OutreachAttachments attachments;
    private final AuditService audit;
    private final Clock clock;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onOptedOut(PersonOptedOut optedOut) {
        stopEverything(optedOut.workspaceId(), optedOut.personId());
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onObjected(PersonPrivacyRequested requested) {
        if (requested.kind() == PrivacyRequestKind.OBJECTION) {
            stopEverything(requested.workspaceId(), requested.personId());
        }
    }

    private void stopEverything(UUID workspaceId, UUID personId) {
        Instant now = clock.instant();
        enrollments.findByWorkspaceIdAndPersonIdAndStatusIn(workspaceId, personId, EnrollmentStatus.LIVE)
                .forEach(enrollment -> outcomes.stop(enrollment, OutreachStopReason.OPTED_OUT, null, now, null));
        messages.findByWorkspaceIdAndPersonIdAndStatus(workspaceId, personId, MessageStatus.SCHEDULED).stream()
                .filter(message -> message.getSendingSince() == null)
                .forEach(message -> {
                    message.fail(OutreachStopReason.OPTED_OUT, now);
                    if (!message.keepsAttachmentFiles()) {
                        attachments.deleteFilesOnCommit(message.getId());
                    }
                    audit.projectEvent(ProjectEventType.OUTREACH_MESSAGE_FAILED, message.getSenderUserId(),
                                    message.getWorkspaceId(), message.getProjectId(), null)
                            .detail("messageId", message.getId().toString())
                            .detail("reason", OutreachStopReason.OPTED_OUT.name())
                            .record();
                });
    }
}
