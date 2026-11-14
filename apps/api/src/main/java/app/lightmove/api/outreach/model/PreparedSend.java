package app.lightmove.api.outreach.model;

import app.lightmove.api.candidate.model.OutreachPrivacyFooter;
import java.util.UUID;

/**
 * An email the re-check cleared and the dispatcher is about to hand to the mail service. {@code step} is zero-based;
 * {@code text} is the plain text the HTML was rendered from, kept for the conversation. {@code email} is as the ledger
 * keeps it; {@code footer} (#924) is added only as it leaves, and is null when data protection is off.
 */
public record PreparedSend(UUID enrollmentId, String grantId, String fromAddress, OutgoingEmail email, String text,
                           int step, OutreachPrivacyFooter footer) {

    public OutgoingEmail outgoing() {
        return email.withPrivacyFooter(footer);
    }
}
