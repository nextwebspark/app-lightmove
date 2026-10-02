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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * An executive booked a time through a consultant's link. The page books without a session, so the
 * address typed into it proves nothing on its own: only a booking made with an address this consultant
 * actually emailed counts. It keeps the call as that person's meeting, ends the consultant's runs with
 * them still listening, and moves them forward to Engaged on each position they were approached for,
 * whether or not they had replied first.
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
        List<String> bookers = booking.participantAddresses().stream()
                .filter(address -> !address.equalsIgnoreCase(mailbox.getAddress()))
                .map(address -> address.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (bookers.isEmpty()) {
            return;
        }
        List<OutreachEnrollment> emailed = enrollments.findEmailedBy(mailbox.getWorkspaceId(), mailbox.getUserId(),
                bookers);
        if (emailed.isEmpty()) {
            return;
        }
        if (booking.eventId() != null) {
            CalendarEvent call = new CalendarEvent(booking.eventId(), booking.title(), booking.startsAt(),
                    booking.endsAt(), booking.participantAddresses(), null, null);
            emailed.stream().map(OutreachEnrollment::getPersonId).distinct()
                    .forEach(personId -> meetings.keep(mailbox, call, personId, null, true));
        }
        emailed.stream()
                .filter(OutreachEnrollment::isListening)
                .forEach(enrollment -> outcomes.booked(enrollment, null, true, now, null));
        latestPerPosition(emailed).forEach(enrollment -> engaged(enrollment, booking));
    }

    /** One line and one move per position, however many runs that position had with them. */
    private static List<OutreachEnrollment> latestPerPosition(List<OutreachEnrollment> emailed) {
        Map<UUID, OutreachEnrollment> latest = emailed.stream()
                .filter(enrollment -> enrollment.getCandidateId() != null)
                .collect(Collectors.toMap(OutreachEnrollment::getProjectId, Function.identity(),
                        (first, second) -> first.getEnrolledAt().isAfter(second.getEnrolledAt()) ? first : second));
        return List.copyOf(latest.values());
    }

    private void engaged(OutreachEnrollment enrollment, BookingMade booking) {
        people.recordMeetingBooked(null, enrollment.getProjectId(), enrollment.getCandidateId(), booking.startsAt(),
                true);
        audit.projectEvent(ProjectEventType.OUTREACH_MEETING_BOOKED, null, enrollment.getWorkspaceId(),
                        enrollment.getProjectId(), null)
                .detail("enrollmentId", enrollment.getId().toString())
                .detail("candidateId", enrollment.getCandidateId().toString())
                .detail("startsAt", booking.startsAt().toString())
                .detail("viaLink", "true")
                .record();
    }
}
