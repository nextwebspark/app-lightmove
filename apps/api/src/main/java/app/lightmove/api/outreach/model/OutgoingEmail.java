package app.lightmove.api.outreach.model;

import app.lightmove.api.candidate.model.OutreachPrivacyFooter;
import java.util.List;

/**
 * One email to one recipient, sent as the connected mailbox. {@code htmlBody} is already escaped.
 * {@code replyToMessageId}, when set, is the earlier email this one answers, which keeps it in that thread.
 * {@code attachments} is empty for every sequence step; only a consultant's own email carries files. {@code headers}
 * are the extra ones a gateway writes where its provider takes them.
 */
public record OutgoingEmail(String to, String subject, String htmlBody, String replyToMessageId,
                            List<EmailAttachment> attachments, List<MailHeader> headers) {

    public OutgoingEmail {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        headers = headers == null ? List.of() : List.copyOf(headers);
    }

    public OutgoingEmail(String to, String subject, String htmlBody, String replyToMessageId,
                         List<EmailAttachment> attachments) {
        this(to, subject, htmlBody, replyToMessageId, attachments, List.of());
    }

    public OutgoingEmail(String to, String subject, String htmlBody, String replyToMessageId) {
        this(to, subject, htmlBody, replyToMessageId, List.of(), List.of());
    }

    public OutgoingEmail(String to, String subject, String htmlBody) {
        this(to, subject, htmlBody, null, List.of(), List.of());
    }

    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }

    public OutgoingEmail carrying(List<EmailAttachment> files) {
        return new OutgoingEmail(to, subject, htmlBody, replyToMessageId, files, headers);
    }

    /**
     * The email as it leaves: the privacy footer after everything else, signature included, and the one-click
     * unsubscribe headers (RFC 8058). None when data protection is off.
     */
    public OutgoingEmail withPrivacyFooter(OutreachPrivacyFooter footer) {
        if (footer == null) {
            return this;
        }
        return new OutgoingEmail(to, subject, htmlBody + footer.html(), replyToMessageId, attachments, List.of(
                new MailHeader("List-Unsubscribe", "<" + footer.oneClickUrl() + ">"),
                new MailHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click")));
    }
}
