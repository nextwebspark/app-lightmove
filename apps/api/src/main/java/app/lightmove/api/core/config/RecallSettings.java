package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Recall.ai's Calendar API — {@code lightmove.recall.*}, one account for the platform, never one per workspace.
 * The region is the base URL: each Recall region is a separate account with its own key. A blank key is a
 * deployment without Recall, where calendars are read directly whatever a workspace chose. The Notetaker's bots ride
 * the same account; their events arrive on an endpoint of their own, signed with {@code botWebhookSecret}.
 */
public record RecallSettings(
        String apiKey,
        String webhookSecret,
        String botWebhookSecret,
        @DefaultValue("https://us-east-1.recall.ai") String baseUrl,
        /** How often live Notetaker bots past their call are read back. Spelled out in yml: {@code @Scheduled} reads it. */
        @DefaultValue("PT10M") Duration botReconcileInterval,
        /** How long a Notetaker transcript is kept once nothing needs it; null keeps it until its person or position goes. */
        Duration transcriptRetention,
        /** The live copilot's shortest gap between two coach runs on one call: what bounds its model calls per minute. */
        @DefaultValue("PT15S") Duration copilotCoachInterval,
        /** The most coach runs one call gets, about an hour at the interval; past it the copilot's reading freezes. */
        @DefaultValue("240") int copilotMaxCoachRuns
) {

    public RecallSettings {
        if (transcriptRetention != null && (transcriptRetention.isNegative() || transcriptRetention.isZero())) {
            throw new IllegalArgumentException(
                    "lightmove.recall.transcript-retention must be positive, but was " + transcriptRetention);
        }
        if (copilotCoachInterval == null || copilotCoachInterval.isNegative()) {
            copilotCoachInterval = Duration.ofSeconds(15);
        }
        if (copilotMaxCoachRuns <= 0) {
            copilotMaxCoachRuns = 240;
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Calendars go through Recall only where its calendar events can be heard: a key set for the Notetaker alone must
     * not start handing every workspace's calendar tokens to Recall.
     */
    public boolean isCalendarOffered() {
        return isConfigured() && webhookSecret != null && !webhookSecret.isBlank();
    }

    /** A bot is offered only where its events can be heard: without the secret its webhook refuses everything. */
    public boolean isBotOffered() {
        return isConfigured() && botWebhookSecret != null && !botWebhookSecret.isBlank();
    }

    @Override
    public String toString() {
        return "RecallSettings[apiKey=<redacted>, webhookSecret=<redacted>, botWebhookSecret=<redacted>, baseUrl="
                + baseUrl + "]";
    }
}
