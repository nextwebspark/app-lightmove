package app.lightmove.api.outreach.model;

/** The provider withdrew the mail service's access to a mailbox; only reconnecting brings it back. */
public record MailboxAccessWithdrawn(String grantId) implements MailboxEvent {}
