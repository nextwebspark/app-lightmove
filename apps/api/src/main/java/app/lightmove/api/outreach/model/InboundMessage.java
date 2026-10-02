package app.lightmove.api.outreach.model;

/**
 * A message arrived in a thread of a connected mailbox. Only who sent it and where — never what it
 * says: a reply's content is the consultant's, and is never read or stored.
 */
public record InboundMessage(String grantId, String threadId, String fromAddress) implements MailboxEvent {}
