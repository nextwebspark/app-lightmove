package app.uncava.api.outreach.service;

import app.uncava.api.outreach.constant.EnrollmentStatus;
import app.uncava.api.outreach.constant.OutreachStopReason;
import app.uncava.api.outreach.repository.OutreachEnrollmentRepository;
import app.uncava.api.project.model.ProjectArchived;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * An archived position sends nothing more: every scheduled or active run on it stops, each with the timeline line
 * and audit event a consultant's Stop writes, in the transaction that archives it. Restoring starts none again.
 */
@Component
@RequiredArgsConstructor
class PositionArchiveOutreach {

    private final OutreachEnrollmentRepository enrollments;
    private final OutreachOutcomes outcomes;
    private final Clock clock;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onArchived(ProjectArchived archived) {
        Instant now = clock.instant();
        enrollments.findByProjectIdAndStatusIn(archived.projectId(), EnrollmentStatus.LIVE)
                .forEach(enrollment -> outcomes.stop(enrollment, OutreachStopReason.POSITION_ARCHIVED,
                        archived.actorUserId(), now, null));
    }
}
