package app.lightmove.api.email;

import app.lightmove.api.common.config.LightMoveProperties;
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
    static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient client;
    private final String from;

    public ResendEmailSender(LightMoveProperties.Email config, RestClient.Builder builder) {
        this.from = "%s <%s>".formatted(config.fromName(), config.fromAddress());
        this.client = builder
                .baseUrl(config.resend().baseUrl())
                .defaultHeader("Authorization", "Bearer " + config.resend().apiKey())
                .build();
    }

    @Override
    public void send(EmailMessage message) {
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

            log.debug("Sent '{}' to {}", message.subject(), message.to());
        } catch (RuntimeException ex) {
            // Swallowed by contract (see EmailSender): a mail API's bad minute must not roll back the
            // signup. Recipient logged, body not — a verification link in a log is a live credential.
            log.error("Failed to send '{}' to {}", message.subject(), message.to(), ex);
        }
    }
}
