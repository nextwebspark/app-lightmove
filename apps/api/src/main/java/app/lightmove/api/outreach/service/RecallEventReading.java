package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.RecallCalendarEvent;
import tools.jackson.databind.JsonNode;

/**
 * A Recall-listed event read as the direct gateway reads the same event, keyed the same way, so a meeting found
 * through Recall and one read or booked directly are one row.
 */
final class RecallEventReading {

    private static final String GOOGLE = "google_calendar";
    private static final String MICROSOFT = "microsoft_outlook";

    private RecallEventReading() {
    }

    /** The meeting key: Google's event id, or Outlook's iCalUId — Recall's Outlook ids are not the immutable ones. */
    static String keyOf(RecallCalendarEvent event) {
        JsonNode raw = event.raw();
        return switch (String.valueOf(event.platform())) {
            case GOOGLE -> firstOf(raw == null ? null : textOf(raw.get("id")), event.platformId());
            case MICROSOFT -> firstOf(raw == null ? null : textOf(raw.get("iCalUId")), event.icalUid());
            default -> null;
        };
    }

    /** Null for a deleted event, or one the gateway itself would not keep: cancelled, all-day or recurring. */
    static CalendarEvent eventOf(RecallCalendarEvent event) {
        if (event.deleted() || event.raw() == null) {
            return null;
        }
        return switch (String.valueOf(event.platform())) {
            case GOOGLE -> GoogleMailboxGateway.eventOf(event.raw());
            case MICROSOFT -> MicrosoftMailboxGateway.eventOf(event.raw());
            default -> null;
        };
    }

    private static String firstOf(String preferred, String fallback) {
        return preferred != null ? preferred : fallback;
    }

    private static String textOf(JsonNode node) {
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText();
    }
}
