package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.outreach.model.BookingMade;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.OutreachEnrollment;
import app.lightmove.api.outreach.repository.OutreachEnrollmentRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * An executive booked a time through a consultant's link. Matched by the address they booked with: it
 * keeps the call as their meeting, ends the run that consultant has with them before its next email, and
 * moves them forward to Engaged on that position.
 */
@Component
@RequiredArgsConstructor
class LinkBookings {

    private final OutreachEnrollmentRepository enrollments;
    private final CandidateOutreachService people;
    private final MeetingSync meetings;
    private final OutreachOutcomes outcomes;
    private final AuditService audit;

    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(MailboxConnection mailbox, BookingMade booking, Instant now) {
        List<String> others = booking.participantAddresses().stream()
                .filter(address -> !address.equalsIgnoreCase(mailbox.getAddress()))
                .toList();
        Set<UUID> bookers = new HashSet<>();
        people.personIdsByEmailKey(mailbox.getWorkspaceId(), others).values().forEach(bookers::addAll);
        if (bookers.isEmpty()) {
            return;
        }
        if (booking.eventId() != null) {
            CalendarEvent call = new CalendarEvent(booking.eventId(), booking.title(), booking.startsAt(),
                    booking.endsAt(), booking.participantAddresses(), null, null);
            bookers.forEach(personId -> meetings.keep(mailbox, call, personId, null, true));
        }
        enrollments.findByWorkspaceIdAndSenderUserIdAndPersonIdIn(mailbox.getWorkspaceId(), mailbox.getUserId(),
                        bookers).stream()
                .filter(OutreachEnrollment::isListening)
                .forEach(enrollment -> ended(enrollment, booking, now));
    }

    private void ended(OutreachEnrollment enrollment, BookingMade booking, Instant now) {
        outcomes.booked(enrollment, null, true, now, null);
        if (enrollment.getCandidateId() != null) {
            people.recordMeetingBooked(null, enrollment.getProjectId(), enrollment.getCandidateId(),
                    booking.startsAt(), true);
        }
        audit.projectEvent(ProjectEventType.OUTREACH_MEETING_BOOKED, null, enrollment.getWorkspaceId(),
                        enrollment.getProjectId(), null)
                .detail("enrollmentId", enrollment.getId().toString())
                .detail("startsAt", booking.startsAt().toString())
                .detail("viaLink", "true")
                .detailIfPresent("candidateId", enrollment.getCandidateId() == null ? null
                        : enrollment.getCandidateId().toString())
                .record();
    }
}
