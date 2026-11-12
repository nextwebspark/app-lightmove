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

            log.debug("Sent an email to {}", recipientOf(message));
            return true;
        } catch (RuntimeException ex) {
            // Swallowed by contract (see EmailSender): a mail API's bad minute must not roll back the
            // signup. The body is never logged — a verification link in a log is a live credential — and
            // the recipient only by their domain, since an address in a log outlives its owner (#927). Nor is the
            // subject: an invitation's or a mention's names a colleague.
            log.error("Failed to send an email to {}", recipientOf(message), ex);
            return false;
        }
    }

    private static String recipientOf(EmailMessage message) {
        return message.to().indexOf('@') > 0
                ? "an address at " + EmailAddressValidator.domainOf(message.to())
                : "(no address)";
    }
}
