package app.lightmove.api.core.email.service;
import app.lightmove.api.core.email.model.EmailMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * Prints the email to the console instead of sending it.
 *
 * <p>The default, and the reason signup is fully testable on a fresh clone with no Resend account, no
 * API key and no spend: the verification link appears in the terminal, and you click it.
 *
 * <p>Selected whenever {@code lightmove.email.provider} is not {@code resend}. Configuring production
 * with a missing API key therefore degrades to "emails silently go nowhere", which is why
 * {@link EmailSenderConfig} logs a warning loud enough to notice.
 */
@Slf4j
public class LogEmailSender implements EmailSender {

    @Override
    public void send(EmailMessage message) {
        log.info("""

                ┌─────────────────────────────────────────────────────────────────
                │ EMAIL (not sent — provider is 'log')
                │ To:      {}
                │ Subject: {}
                ├─────────────────────────────────────────────────────────────────
                {}
                └─────────────────────────────────────────────────────────────────
                """, message.to(), message.subject(), message.textBody());
    }
}
