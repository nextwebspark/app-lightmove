package app.lightmove.api.email;

import app.lightmove.api.common.config.LightMoveProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Picks the {@link EmailSender} adapter from config — the one place that knows which provider is live.
 */
@Configuration
public class EmailSenderConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderConfig.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    EmailSender emailSender(LightMoveProperties properties) {
        LightMoveProperties.Email config = properties.email();

        if (!"resend".equalsIgnoreCase(config.provider())) {
            log.info("Email provider is '{}' — messages will be printed, not sent.", config.provider());
            return new LogEmailSender();
        }

        // Fail at startup rather than at the first signup. A production instance that boots happily
        // and then silently drops every verification email is far worse than one that refuses to boot.
        if (config.resend().apiKey() == null || config.resend().apiKey().isBlank()) {
            throw new IllegalStateException(
                    "lightmove.email.provider=resend but no API key is set (RESEND_API_KEY)");
        }

        log.info("Email provider is Resend, sending as {}", config.fromAddress());
        return new ResendEmailSender(config, RestClient.builder().requestFactory(timeoutBoundFactory()));
    }

    /**
     * Timeouts are the point of this factory. Without them a hung mail API holds a request thread
     * until the socket gives up, and a user waits on their signup for as long as Resend takes to fail.
     */
    private static JdkClientHttpRequestFactory timeoutBoundFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(ResendEmailSender.READ_TIMEOUT);
        return factory;
    }
}
