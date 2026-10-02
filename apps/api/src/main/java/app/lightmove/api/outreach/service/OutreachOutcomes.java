package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.outreach.constant.EnrollmentStatus;
import app.lightmove.api.outreach.constant.OutreachStopReason;
import app.lightmove.api.outreach.model.OutreachEnrollment;
import app.lightmove.api.outreach.model.OutreachSequence;
import app.lightmove.api.outreach.repository.OutreachSequenceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * How a run ends — stopped, answered or bounced — each written with its timeline line and audit event in
 * the caller's transaction, whichever of the dispatcher, the webhook, the poll or a consultant ended it.
 */
@Component
@RequiredArgsConstructor
class OutreachOutcomes {

    private final OutreachSequenceRepository sequences;
    private final CandidateOutreachService people;
    private final AuditService audit;

    /** {@code actor} is the consultant who pressed Stop, or null when the send-time re-check ended the run. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void stop(OutreachEnrollment enrollment, OutreachStopReason reason, UUID actor, Instant now,
                     HttpServletRequest request) {
        enrollment.stop(reason, now);
        people.recordStopped(enrollment.getWorkspaceId(), enrollment.getProjectId(), enrollment.getPersonId(), actor,
                enrollment.getSequenceId(), sequenceNameOf(enrollment), reason.name());
        audited(ProjectEventType.OUTREACH_STOPPED, enrollment, actor, request)
                .detail("reason", reason.name())
                .record();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void replied(OutreachEnrollment enrollment, Instant now) {
        enrollment.replied(now);
        people.recordReplied(enrollment.getWorkspaceId(), enrollment.getProjectId(), enrollment.getPersonId(),
                enrollment.getSequenceId(), sequenceNameOf(enrollment));
        audited(ProjectEventType.OUTREACH_REPLIED, enrollment, null, null).record();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void bounced(OutreachEnrollment enrollment, Instant now) {
        enrollment.bounced(now);
        people.recordStopped(enrollment.getWorkspaceId(), enrollment.getProjectId(), enrollment.getPersonId(), null,
                enrollment.getSequenceId(), sequenceNameOf(enrollment), EnrollmentStatus.BOUNCED.name());
        audited(ProjectEventType.OUTREACH_BOUNCED, enrollment, null, null).record();
    }

    /**
     * A call was booked with the person: like a reply, it ends the run. {@code actor} is the consultant who
     * booked it, or null when the executive booked it themselves through the link.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void booked(OutreachEnrollment enrollment, UUID actor, boolean viaLink, Instant now,
                       HttpServletRequest request) {
        enrollment.booked(now, viaLink);
        people.recordStopped(enrollment.getWorkspaceId(), enrollment.getProjectId(), enrollment.getPersonId(), actor,
                enrollment.getSequenceId(), sequenceNameOf(enrollment), EnrollmentStatus.BOOKED.name());
        audited(ProjectEventType.OUTREACH_STOPPED, enrollment, actor, request)
                .detail("reason", EnrollmentStatus.BOOKED.name())
                .detail("viaLink", String.valueOf(viaLink))
                .record();
    }

    String sequenceNameOf(OutreachEnrollment enrollment) {
        return sequences.findById(enrollment.getSequenceId()).map(OutreachSequence::getName).orElse(null);
    }

    private AuditService.Builder audited(ProjectEventType type, OutreachEnrollment enrollment, UUID actor,
                                         HttpServletRequest request) {
        return audit.projectEvent(type, actor, enrollment.getWorkspaceId(), enrollment.getProjectId(), request)
                .detail("sequenceId", enrollment.getSequenceId().toString())
                .detail("enrollmentId", enrollment.getId().toString())
                .detailIfPresent("candidateId", enrollment.getCandidateId() == null ? null
                        : enrollment.getCandidateId().toString());
    }
}
