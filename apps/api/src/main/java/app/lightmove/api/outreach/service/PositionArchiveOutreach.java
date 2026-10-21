package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.constant.EnrollmentStatus;
import app.lightmove.api.outreach.constant.OutreachStopReason;
import app.lightmove.api.outreach.constant.MessageStatus;
import app.lightmove.api.outreach.model.OutreachMessage;
import app.lightmove.api.outreach.repository.OutreachEnrollmentRepository;
import app.lightmove.api.outreach.repository.OutreachMessageRepository;
import app.lightmove.api.project.model.ProjectArchived;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * An archived position sends nothing more: every scheduled or active run on it stops, each with the timeline line
 * and audit event a consultant's Stop writes, and every email a consultant scheduled there and the dispatcher has not
 * yet taken is cancelled, in the transaction that archives it. Restoring starts and schedules none again.
 */
@Component
@RequiredArgsConstructor
class PositionArchiveOutreach {

    private final OutreachEnrollmentRepository enrollments;
    private final OutreachOutcomes outcomes;
    private final OutreachMessageRepository messages;
    private final Clock clock;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onArchived(ProjectArchived archived) {
        Instant now = clock.instant();
        enrollments.findByProjectIdAndStatusIn(archived.projectId(), EnrollmentStatus.LIVE)
                .forEach(enrollment -> outcomes.stop(enrollment, OutreachStopReason.POSITION_ARCHIVED,
                        archived.actorUserId(), now, null));
        // One already taken is on its way, as a consultant's own Cancel finds it; should it be put back unsent,
        // its next dispatch fails it as archived.
        messages.findByProjectIdAndStatus(archived.projectId(), MessageStatus.SCHEDULED).stream()
                .filter(message -> message.getSendingSince() == null)
                .forEach(OutreachMessage::cancel);
    }
}
