package app.lightmove.api.outreach.model;

import java.time.Instant;
import java.util.List;

/**
 * One timed event on a connected calendar, in no service's own words. {@code participantAddresses}
 * includes the organizer; an all-day event is never one of these, since nobody books a call for a day.
 */
public record CalendarEvent(String id, String title, Instant startsAt, Instant endsAt,
                            List<String> participantAddresses, String joinUrl, String conferencingProvider) {

    public CalendarEvent {
        participantAddresses = List.copyOf(participantAddresses);
    }
}
