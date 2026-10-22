package app.lightmove.api.notetaker.config;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.RecallSettings;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.notetaker.service.BotIdentityVerifier;
import app.lightmove.api.notetaker.service.CloudRunJobMeetingRecorder;
import app.lightmove.api.notetaker.service.GoogleAccessTokens;
import app.lightmove.api.notetaker.service.GoogleBotIdentityVerifier;
import app.lightmove.api.notetaker.service.LocalProcessMeetingRecorder;
import app.lightmove.api.notetaker.service.LogMeetingRecorder;
import app.lightmove.api.notetaker.service.MeetingRecorder;
import app.lightmove.api.notetaker.service.NotetakerBotEnvironment;
import app.lightmove.api.notetaker.service.RecallBotApi;
import app.lightmove.api.notetaker.service.RecallBotClient;
import app.lightmove.api.notetaker.service.UnconfiguredRecallBotApi;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;
import org.springframework.web.client.RestClient;

/**
 * The recorder our own bot is started through — chosen by {@code lightmove.notetaker.runner}; a test's recording
 * one replaces it — and Recall's bots behind it (#955).
 */
@Configuration(proxyBeanMethods = false)
public class NotetakerConfig {

    static final String CLOUD_RUN_BASE_URL = "https://run.googleapis.com";

    @Bean
    @Fallback
    MeetingRecorder ownMeetingRecorder(LightMoveProperties properties) {
        return switch (properties.notetaker().runner()) {
            case LOG -> new LogMeetingRecorder();
            case LOCAL -> new LocalProcessMeetingRecorder(properties, repositoryRoot());
            case CLOUD_RUN_JOB -> new CloudRunJobMeetingRecorder(properties, applicationDefaultTokens(),
                    RestClient.builder(), CLOUD_RUN_BASE_URL);
        };
    }

    /**
     * Recall's bots, the backup behind ours, where {@code lightmove.recall} has a key and a bot webhook secret — a bot
     * whose reports would all be refused is never sent — and the deployment has not turned the fallback off. Each
     * workspace still turns it on for itself. A test's recording one replaces it.
     */
    @Bean
    @Fallback
    RecallBotApi recallBotApi(LightMoveProperties properties, VendorClientFactory clientFactory,
                              VendorRateLimiter rateLimiter, VendorCallGuard guard, Clock clock) {
        RecallSettings recall = properties.recall();
        if (recall == null || !recall.isConfigured() || recall.botWebhookSecret() == null
                || recall.botWebhookSecret().isBlank() || !properties.notetaker().fallback()) {
            return new UnconfiguredRecallBotApi();
        }
        return new RecallBotClient(recall, clientFactory, rateLimiter, guard, RestClient.builder(), clock);
    }

    /** Where neither our bots' nor the canary's service account is configured, no ID token is ever taken. */
    @Bean
    @Fallback
    BotIdentityVerifier botIdentityVerifier(LightMoveProperties properties) {
        String bot = properties.notetaker().botServiceAccount();
        String canary = properties.notetaker().canaryServiceAccount();
        if ((bot == null || bot.isBlank()) && (canary == null || canary.isBlank())) {
            return idToken -> Optional.empty();
        }
        return new GoogleBotIdentityVerifier(NotetakerBotEnvironment.tokenAudienceOf(properties));
    }

    /** {@code npm run dev} starts the API in {@code apps/api}; the bot's command runs from the repository root. */
    private static Path repositoryRoot() {
        Path here = Path.of("").toAbsolutePath();
        return here.endsWith(Path.of("apps", "api")) ? here.getParent().getParent() : here;
    }

    private static GoogleAccessTokens applicationDefaultTokens() {
        GoogleCredentials credentials;
        try {
            credentials = GoogleCredentials.getApplicationDefault()
                    .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
        } catch (IOException missing) {
            throw new UncheckedIOException("The cloud-run-job runner needs application default credentials", missing);
        }
        return () -> {
            try {
                credentials.refreshIfExpired();
                return credentials.getAccessToken().getTokenValue();
            } catch (IOException failed) {
                throw new UncheckedIOException("No Google access token for Cloud Run", failed);
            }
        };
    }
}
