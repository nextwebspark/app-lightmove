package app.lightmove.api.core.email.model;

import app.lightmove.api.core.email.constant.EmailSenderIdentity;

/**
 * One outbound email, provider-agnostic.
 *
 * <p>Carries both an HTML and a plain-text body: some clients refuse HTML, and a transactional email
 * with no text alternative is markedly more likely to be scored as spam.
 *
 * <p>{@code senderName} and {@code replyTo} are null for Uncava's own mail. A privacy notice goes on a firm's behalf
 * (#923): the firm's name is shown beside Uncava's address, and a reply reaches the firm's privacy contact.
 */
public record EmailMessage(
        String to,
        String subject,
        String htmlBody,
        String textBody,
        String senderName,
        String replyTo,
        EmailSenderIdentity senderIdentity
) {

    public EmailMessage(String to, String subject, String htmlBody, String textBody) {
        this(to, subject, htmlBody, textBody, null, null, EmailSenderIdentity.ACCOUNT);
    }

    public EmailMessage onBehalfOf(String sender, String replyAddress, EmailSenderIdentity identity) {
        return new EmailMessage(to, subject, htmlBody, textBody, sender, replyAddress, identity);
    }

    public EmailMessage {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Recipient is required");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
        if (senderIdentity == null) {
            throw new IllegalArgumentException("Sender identity is required");
        }
    }
}
