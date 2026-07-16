package app.lightmove.api;

import app.lightmove.api.core.email.model.EmailMessage;
import app.lightmove.api.core.email.service.EmailSender;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * An {@link EmailSender} that keeps what it was asked to send, so a test can read the verification
 * link back out and "click" it.
 *
 * <p>This is the payoff for {@code EmailSender} being a port. The verification flow is exercised
 * end to end — token generated, hashed, emailed, redeemed — with no provider, no network, and no
 * scraping of log output.
 */
public class RecordingEmailSender implements EmailSender {

    /** The token in a verify or invite link — the part a user would carry back in their browser. */
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(EmailMessage message) {
        sent.add(message);
    }

    public void clear() {
        sent.clear();
    }

    public List<EmailMessage> sent() {
        return List.copyOf(sent);
    }

    /** The token from the most recent email to this address. */
    public String latestTokenFor(String recipient) {
        return sent.reversed().stream()
                .filter(message -> recipient.equalsIgnoreCase(message.to()))
                .map(RecordingEmailSender::extractToken)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No email with a token was sent to " + recipient));
    }

    private static String extractToken(EmailMessage message) {
        Matcher matcher = TOKEN.matcher(message.textBody());
        return matcher.find() ? matcher.group(1) : null;
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /** {@code @Primary} so it wins over the LogEmailSender the application would otherwise pick. */
        @Bean
        @Primary
        public RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }
}
