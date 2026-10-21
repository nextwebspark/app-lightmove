package app.lightmove.api.outreach.model;

import java.util.UUID;

/**
 * An email the re-check cleared and the dispatcher is about to hand to the mail service. {@code step} is zero-based;
 * {@code text} is the plain text the HTML was rendered from, kept for the conversation.
 */
public record PreparedSend(UUID enrollmentId, String grantId, String fromAddress, OutgoingEmail email, String text,
                           int step) {}
