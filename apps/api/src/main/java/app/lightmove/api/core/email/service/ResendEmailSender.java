package app.lightmove.api.core.email.service;
import app.lightmove.api.core.email.model.EmailMessage;

import app.lightmove.api.core.config.EmailSettings;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

/**
 * Sends through Resend's HTTP API.
 *
 * <p>Chosen over SMTP because Google Cloud blocks outbound port 25, and over a heavier SDK because
 * the API is one POST — a dependency would buy nothing but a supply-chain surface.
 */
@Slf4j
public class ResendEmailSender implements EmailSender {

    /** Short: a slow mail API must not hold a request thread while a user waits on their signup. */
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient client;
    private final String from;

    public ResendEmailSender(EmailSettings config, RestClient.Builder builder) {
        this.from = "%s <%s>".formatted(config.fromName(), config.fromAddress());
        this.client = builder
                .baseUrl(config.resend().baseUrl())
                .defaultHeader("Authorization", "Bearer " + config.resend().apiKey())
                .build();
    }

    @Override
    public void send(EmailMessage message) {
        trySend(message);
    }

    @Override
    public boolean trySend(EmailMessage message) {
        try {
            client.post()
                    .uri("/emails")
                    .body(Map.of(
                            "from", from,
                            "to", message.to(),
                            "subject", message.subject(),
                            "html", message.htmlBody(),
                            "text", message.textBody()))
                    .retrieve()
                    .toBodilessEntity();

            log.debug("Sent '{}' to an address at {}", message.subject(), recipientDomainOf(message));
            return true;
        } catch (RuntimeException ex) {
            // Swallowed by contract (see EmailSender): a mail API's bad minute must not roll back the
            // signup. The body is never logged — a verification link in a log is a live credential — and
            // the recipient only by their domain, since an address in a log outlives its owner (#927).
            log.error("Failed to send '{}' to an address at {}", message.subject(), recipientDomainOf(message), ex);
            return false;
        }
    }

    private static String recipientDomainOf(EmailMessage message) {
        String to = message.to();
        int at = to == null ? -1 : to.lastIndexOf('@');
        return at < 0 ? "an unreadable address" : to.substring(at + 1);
    }
}
