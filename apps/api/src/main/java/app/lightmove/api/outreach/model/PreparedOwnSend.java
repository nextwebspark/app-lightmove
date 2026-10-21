package app.lightmove.api.outreach.model;

import java.util.UUID;

/** A consultant's claimed email, checked and ready for the mail service. */
public record PreparedOwnSend(UUID messageId, String grantId, OutgoingEmail email) {}
