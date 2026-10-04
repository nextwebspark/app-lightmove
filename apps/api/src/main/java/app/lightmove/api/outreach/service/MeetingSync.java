package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.repository.PersonMeetingRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        applyAll(mailbox, List.of(event));
    }

    /**
     * A whole calendar read at once: one query for every address on it, and a delete only for an event
     * this mailbox already holds — on a first read that is none of them.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyAll(MailboxConnection mailbox, Collection<CalendarEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        Map<String, Set<UUID>> holders = people.personIdsByEmailKey(mailbox.getWorkspaceId(),
                events.stream().flatMap(event -> othersOn(mailbox, event).stream()).toList());
        Set<String> held = new HashSet<>(meetings.findHeldEventIds(mailbox.getId(),
                events.stream().map(CalendarEvent::id).toList()));
        for (CalendarEvent event : events) {
            Set<UUID> attending = new HashSet<>();
            othersOn(mailbox, event).forEach(address ->
                    attending.addAll(holders.getOrDefault(CandidateOutreachService.emailKeyOf(address), Set.of())));
            if (attending.isEmpty()) {
                if (held.contains(event.id())) {
                    meetings.deleteEvent(mailbox.getId(), event.id());
                }
                continue;
            }
            if (held.contains(event.id())) {
                meetings.deleteDroppedFromEvent(mailbox.getId(), event.id(), attending);
            }
            attending.forEach(personId -> keep(mailbox, event, personId, null, false));
        }
    }

    /**
     * {@code bookedBy} is the consultant who booked it through Uncava, or null for an event found on a calendar
     * or one the executive booked through the link ({@code viaLink}).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void keep(MailboxConnection mailbox, CalendarEvent event, UUID personId, UUID bookedBy, boolean viaLink) {
        meetings.upsert(mailbox.getWorkspaceId(), personId, mailbox.getId(), mailbox.getUserId(), event.id(),
                event.title(), event.startsAt(), event.endsAt(), event.joinUrl(), event.conferencingProvider(),
                bookedBy, viaLink);
    }

    /**
     * A full read of {@code from}–{@code to}: what it found is kept, and a meeting it did not find in that window is
     * gone — the calendar's read is the only word on it, since nothing pushes a change.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceWindow(MailboxConnection mailbox, Instant from, Instant to, Collection<CalendarEvent> found) {
        applyAll(mailbox, found);
        if (found.isEmpty()) {
            meetings.deleteAllFrom(mailbox.getId(), from, to);
        } else {
            meetings.deleteMissingFrom(mailbox.getId(), from, to, found.stream().map(CalendarEvent::id).toList());
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void remove(MailboxConnection mailbox, String eventId) {
        meetings.deleteEvent(mailbox.getId(), eventId);
    }

    private static List<String> othersOn(MailboxConnection mailbox, CalendarEvent event) {
        return event.participantAddresses().stream()
                .filter(address -> !address.equalsIgnoreCase(mailbox.getAddress()))
                .toList();
    }
}
