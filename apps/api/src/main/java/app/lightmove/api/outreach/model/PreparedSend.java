package app.lightmove.api.outreach.model;

import java.util.UUID;

/** An email the re-check cleared and the dispatcher is about to hand to the mail service. {@code step} is zero-based. */
public record PreparedSend(UUID enrollmentId, String grantId, OutgoingEmail email, int step) {}
