package app.lightmove.api.outreach.model;

import java.util.List;

/**
 * One email to one recipient, sent as the connected mailbox. {@code htmlBody} is already escaped.
 * {@code replyToMessageId}, when set, is the earlier email this one answers, which keeps it in that thread.
 * {@code attachments} is empty for every sequence step; only a consultant's own email carries files.
 */
public record OutgoingEmail(String to, String subject, String htmlBody, String replyToMessageId,
                            List<EmailAttachment> attachments) {

    public OutgoingEmail {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public OutgoingEmail(String to, String subject, String htmlBody, String replyToMessageId) {
        this(to, subject, htmlBody, replyToMessageId, List.of());
    }

    public OutgoingEmail(String to, String subject, String htmlBody) {
        this(to, subject, htmlBody, null, List.of());
    }

    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }
}
