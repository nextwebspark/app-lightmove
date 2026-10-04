package app.lightmove.api.core.email.service;
import app.lightmove.api.core.email.model.EmailMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * Prints the email to the console — the default whenever {@code lightmove.email.provider} is not
 * {@code resend}, so production missing a key silently sends nothing ({@link EmailSenderConfig} warns).
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
