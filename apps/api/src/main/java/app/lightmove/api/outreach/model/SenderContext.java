package app.lightmove.api.outreach.model;

/** The tokens one press shares across everyone in it: the position's title and the sender's first name. */
public record SenderContext(String positionTitle, String senderFirstName) {}
