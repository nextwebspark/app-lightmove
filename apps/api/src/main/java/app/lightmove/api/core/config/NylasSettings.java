package app.lightmove.api.core.config;

import java.util.List;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code lightmove.outreach.nylas.*}; a blank key or client id offers no outreach email. A blank {@code webhookSecret}
 * refuses every delivery (the poll finds replies); {@code schedulerEnabled} gates {@code {{bookingLink}}};
 * {@code readsReplies} keeps a reply's words, since no grant reports whether the app's scopes reach a body.
 */
public record NylasSettings(
        String apiKey,
        String clientId,
        @DefaultValue("https://api.us.nylas.com") String baseUrl,
        @DefaultValue("google,microsoft") List<String> providers,
        @DefaultValue("5") int requestsPerSecond,
        String webhookSecret,
        @DefaultValue("false") boolean schedulerEnabled,
        @DefaultValue("false") boolean readsReplies
) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && clientId != null && !clientId.isBlank();
    }
}
