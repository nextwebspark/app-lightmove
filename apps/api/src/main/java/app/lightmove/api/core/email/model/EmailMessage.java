package app.lightmove.api.core.email.model;

/**
 * One outbound email, provider-agnostic.
 *
 * <p>Carries both an HTML and a plain-text body: some clients refuse HTML, and a transactional email
 * with no text alternative is markedly more likely to be scored as spam.
 */
public record EmailMessage(
        String to,
        String subject,
        String htmlBody,
        String textBody
) {

    public EmailMessage {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Recipient is required");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
    }
}
