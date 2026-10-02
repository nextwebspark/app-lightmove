package app.lightmove.api.outreach.model;

/** An event on a connected calendar was created or changed. */
public record CalendarEventChanged(String grantId, CalendarEvent event) implements MailboxEvent {}
