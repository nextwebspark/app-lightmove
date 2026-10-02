package app.lightmove.api.outreach.model;

/** An event on a connected calendar was deleted or cancelled. */
public record CalendarEventRemoved(String grantId, String eventId) implements MailboxEvent {}
