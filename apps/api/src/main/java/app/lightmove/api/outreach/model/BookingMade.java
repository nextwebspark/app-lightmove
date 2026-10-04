package app.lightmove.api.outreach.model;

import java.time.Instant;
import java.util.List;

/**
 * Someone booked a time through a consultant's booking page. Named by the page's configuration, since a
 * booking may arrive without the grant; {@code participantAddresses} includes the consultant's own.
 */
public record BookingMade(String grantId, String configurationId, String eventId, String title, Instant startsAt,
                          Instant endsAt, List<String> participantAddresses) implements MailboxEvent {

    public BookingMade {
        participantAddresses = List.copyOf(participantAddresses);
    }
}
