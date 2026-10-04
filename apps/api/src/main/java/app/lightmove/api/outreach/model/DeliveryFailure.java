package app.lightmove.api.outreach.model;

/** An email the mailbox sent bounced. Either id may be missing, depending on what the provider reported. */
public record DeliveryFailure(String grantId, String threadId, String messageId) implements MailboxEvent {}
