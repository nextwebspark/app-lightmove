package app.lightmove.api.outreach.model;

/** One email to one recipient, sent as the connected mailbox. {@code htmlBody} is already escaped. */
public record OutgoingEmail(String to, String subject, String htmlBody) {}
