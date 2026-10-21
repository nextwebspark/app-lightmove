package app.lightmove.api.outreach.model;

import java.util.List;
import java.util.UUID;

/** A consultant's claimed email, checked and ready for the mail service. */
public record PreparedOwnSend(UUID messageId, String grantId, OutgoingEmail email) {

    /** The same email with the files it carries, read once the claim's transaction has closed. */
    public PreparedOwnSend carrying(List<EmailAttachment> files) {
        return new PreparedOwnSend(messageId, grantId, new OutgoingEmail(email.to(), email.subject(),
                email.htmlBody(), email.replyToMessageId(), files));
    }
}
