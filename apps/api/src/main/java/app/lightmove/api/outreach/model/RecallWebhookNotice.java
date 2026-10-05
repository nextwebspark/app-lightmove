package app.lightmove.api.outreach.model;

import java.time.Instant;

/** What one verified Recall delivery says about a calendar. */
public sealed interface RecallWebhookNotice {

    String calendarId();

    /** {@code calendar.update}: the calendar's own state changed — a refused refresh among others. */
    record CalendarStateChanged(String calendarId) implements RecallWebhookNotice {}

    /** {@code calendar.sync_events}: events on the calendar changed at or after {@code since}. */
    record CalendarEventsChanged(String calendarId, Instant since) implements RecallWebhookNotice {}
}
