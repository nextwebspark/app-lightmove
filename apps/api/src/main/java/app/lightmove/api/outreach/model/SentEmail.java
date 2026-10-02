package app.lightmove.api.outreach.model;

/** The mail service's ids for a sent email: a follow-up replies to {@code messageId} to land in its thread. */
public record SentEmail(String messageId, String threadId) {}
