package app.lightmove.api.outreach.model;

/**
 * The tokens one press shares across everyone in it: the position's title, the sender's first name, and
 * the sender's booking link, or null while they have none.
 */
public record SenderContext(String positionTitle, String senderFirstName, String bookingLink) {}
