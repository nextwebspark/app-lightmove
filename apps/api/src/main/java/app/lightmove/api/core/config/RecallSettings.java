package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Recall.ai's Calendar API — {@code lightmove.recall.*}, one account for the platform, never one per workspace.
 * The region is the base URL: each Recall region is a separate account with its own key. A blank key is a
 * deployment without Recall, where calendars are read directly whatever a workspace chose.
 */
public record RecallSettings(
        String apiKey,
        String webhookSecret,
        @DefaultValue("https://us-east-1.recall.ai") String baseUrl
) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
