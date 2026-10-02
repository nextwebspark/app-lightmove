package app.lightmove.api.outreach.model;

/**
 * One email to one recipient, sent as the connected mailbox. {@code htmlBody} is already escaped.
 * {@code replyToMessageId}, when set, is the earlier email this one answers, which keeps it in that thread.
 */
public record OutgoingEmail(String to, String subject, String htmlBody, String replyToMessageId) {

    public OutgoingEmail(String to, String subject, String htmlBody) {
        this(to, subject, htmlBody, null);
    }
}
