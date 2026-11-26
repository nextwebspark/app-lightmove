package app.lightmove.api.core.email.service;

import app.lightmove.api.core.config.EmailSettings;
import app.lightmove.api.core.email.model.EmailMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * Prints the email to the console — the default whenever {@code lightmove.email.provider} is not
 * {@code resend}, so production missing a key silently sends nothing ({@link EmailSenderConfig} warns).
 * The From line is the header Resend would be given, so which sending address a mail picks is visible locally.
 */
@Slf4j
public class LogEmailSender implements EmailSender {

    private final EmailFromHeader fromHeader;

    public LogEmailSender(EmailSettings settings) {
        this.fromHeader = new EmailFromHeader(settings);
    }

    @Override
    public void send(EmailMessage message) {
        log.info("""

                ┌─────────────────────────────────────────────────────────────────
                │ EMAIL (not sent — provider is 'log')
                │ To:      {}
                │ From:    {}
                │ Reply:   {}
                │ Subject: {}
                ├─────────────────────────────────────────────────────────────────
                {}
                └─────────────────────────────────────────────────────────────────
                """, message.to(), fromHeader.of(message),
                message.replyTo() == null ? "-" : message.replyTo(), message.subject(), message.textBody());
    }
}
