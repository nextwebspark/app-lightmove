package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.repository.PersonMeetingRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps a calendar event as a meeting for each mapped person on it, and for nobody else: an event none
 * of the workspace's people attend leaves no row, so the rest of a consultant's calendar is never kept.
 */
@Component
@RequiredArgsConstructor
class MeetingSync {

    private final PersonMeetingRepository meetings;
    private final CandidateOutreachService people;

    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(MailboxConnection mailbox, CalendarEvent event) {
        List<String> others = event.participantAddresses().stream()
                .filter(address -> !address.equalsIgnoreCase(mailbox.getAddress()))
                .toList();
        Set<UUID> attending = people.personIdsHolding(mailbox.getWorkspaceId(), others);
        if (attending.isEmpty()) {
            meetings.deleteEvent(mailbox.getId(), event.id());
            return;
        }
        meetings.deleteDroppedFromEvent(mailbox.getId(), event.id(), attending);
        attending.forEach(personId -> keep(mailbox, event, personId, null));
    }

    /** {@code bookedBy} is the consultant who booked it through Uncava, or null for an event found on a calendar. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void keep(MailboxConnection mailbox, CalendarEvent event, UUID personId, UUID bookedBy) {
        meetings.upsert(mailbox.getWorkspaceId(), personId, mailbox.getId(), mailbox.getUserId(), event.id(),
                event.title(), event.startsAt(), event.endsAt(), event.joinUrl(), event.conferencingProvider(),
                bookedBy, false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void remove(MailboxConnection mailbox, String eventId) {
        meetings.deleteEvent(mailbox.getId(), eventId);
    }
}
