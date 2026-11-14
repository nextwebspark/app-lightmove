package app.lightmove.api.outreach.model;

import app.lightmove.api.candidate.model.OutreachPrivacyFooter;
import java.util.List;
import java.util.UUID;

/**
 * A consultant's claimed email, checked and ready for the mail service. {@code email} is as the ledger keeps it;
 * {@code footer} (#924) is added only as it leaves, and is null when data protection is off.
 */
public record PreparedOwnSend(UUID messageId, String grantId, OutgoingEmail email, OutreachPrivacyFooter footer) {

    /** The same email with the files it carries, read once the claim's transaction has closed. */
    public PreparedOwnSend carrying(List<EmailAttachment> files) {
        return new PreparedOwnSend(messageId, grantId, email.carrying(files), footer);
    }

    public OutgoingEmail outgoing() {
        return email.withPrivacyFooter(footer);
    }
}
