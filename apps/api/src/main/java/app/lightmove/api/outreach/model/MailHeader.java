package app.lightmove.api.outreach.model;

/** One header an outgoing email carries beyond the ones every gateway writes, such as {@code List-Unsubscribe}. */
public record MailHeader(String name, String value) {}
