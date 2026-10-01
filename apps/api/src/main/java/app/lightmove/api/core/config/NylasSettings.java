package app.lightmove.api.core.config;

import java.util.List;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The Nylas application consultants connect their own mailbox through —
 * {@code lightmove.outreach.nylas.*}. A blank key or client id is a deployment without outreach email:
 * nothing is offered, and the boot does not fail.
 *
 * <p>{@code providers} is what Nylas calls the mailbox's host ({@code google}, {@code microsoft}), passed
 * through to its hosted sign-in as configured; nothing branches on one.
 */
public record NylasSettings(
        String apiKey,
        String clientId,
        @DefaultValue("https://api.us.nylas.com") String baseUrl,
        @DefaultValue("google,microsoft") List<String> providers,
        @DefaultValue("5") int requestsPerSecond
) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && clientId != null && !clientId.isBlank();
    }
}
