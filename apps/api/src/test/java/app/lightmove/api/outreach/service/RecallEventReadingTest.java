package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.RecallCalendarEvent;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A Recall-listed event reads as the direct gateway reads the same event, under the same key. */
class RecallEventReadingTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("a Google event is read from its raw copy and keyed on Google's own event id")
    void googleEventsReadAsTheGatewayReadsThem() {
        RecallCalendarEvent listed = new RecallCalendarEvent("google_calendar", "evt-1", "evt-1@google.com", raw("""
                {"id":"evt-1","status":"confirmed","summary":"First conversation",
                 "start":{"dateTime":"2026-10-06T10:00:00+04:00"},"end":{"dateTime":"2026-10-06T10:30:00+04:00"},
                 "organizer":{"email":"yara@firm.example"},"attendees":[{"email":"priya@client.example"}],
                 "hangoutLink":"https://meet.google.com/abc-defg-hij"}"""), false);

        CalendarEvent event = RecallEventReading.eventOf(listed);

        assertThat(RecallEventReading.keyOf(listed)).isEqualTo("evt-1");
        assertThat(event.id()).isEqualTo("evt-1");
        assertThat(event.startsAt()).isEqualTo(Instant.parse("2026-10-06T06:00:00Z"));
        assertThat(event.participantAddresses()).containsExactly("priya@client.example", "yara@firm.example");
        assertThat(event.joinUrl()).isEqualTo("https://meet.google.com/abc-defg-hij");
    }

    @Test
    @DisplayName("an Outlook event is keyed on its iCalUId, the key a direct read and a booking use too")
    void outlookEventsAreKeyedOnTheirICalUId() {
        RecallCalendarEvent listed = new RecallCalendarEvent("microsoft_outlook", "AAMkOrdinaryId",
                "040000008200E00074C5B7101A82E008", raw("""
                {"id":"AAMkOrdinaryId","iCalUId":"040000008200E00074C5B7101A82E008","subject":"First conversation",
                 "type":"singleInstance","isAllDay":false,"isCancelled":false,
                 "start":{"dateTime":"2026-10-06T06:00:00.0000000","timeZone":"UTC"},
                 "end":{"dateTime":"2026-10-06T06:30:00.0000000","timeZone":"UTC"},
                 "attendees":[{"emailAddress":{"address":"priya@client.example"}}],
                 "organizer":{"emailAddress":{"address":"yara@firm.example"}}}"""), false);

        assertThat(RecallEventReading.keyOf(listed)).isEqualTo("040000008200E00074C5B7101A82E008");
        assertThat(RecallEventReading.eventOf(listed).id()).isEqualTo("040000008200E00074C5B7101A82E008");
    }

    @Test
    @DisplayName("a deleted, cancelled or recurring event reads as nothing kept, and is still keyed for removal")
    void goneEventsStillHaveAKey() {
        RecallCalendarEvent deleted = new RecallCalendarEvent("microsoft_outlook", "AAMk2", "040000008200E0", null,
                true);
        RecallCalendarEvent cancelled = new RecallCalendarEvent("google_calendar", "evt-2", "evt-2@google.com",
                raw("{\"id\":\"evt-2\",\"status\":\"cancelled\"}"), false);

        assertThat(RecallEventReading.eventOf(deleted)).isNull();
        assertThat(RecallEventReading.keyOf(deleted)).isEqualTo("040000008200E0");
        assertThat(RecallEventReading.eventOf(cancelled)).isNull();
        assertThat(RecallEventReading.keyOf(cancelled)).isEqualTo("evt-2");
    }

    private static JsonNode raw(String json) {
        return JSON.readTree(json);
    }
}
