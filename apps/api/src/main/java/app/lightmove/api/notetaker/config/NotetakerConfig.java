package app.lightmove.api.notetaker.config;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.notetaker.model.OfferedPlatforms;
import app.lightmove.api.notetaker.service.CloudRunJobMeetingRecorder;
import app.lightmove.api.notetaker.service.GoogleAccessTokens;
import app.lightmove.api.notetaker.service.LocalProcessMeetingRecorder;
import app.lightmove.api.notetaker.service.LogMeetingRecorder;
import app.lightmove.api.notetaker.service.MeetingRecorder;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;
import org.springframework.web.client.RestClient;

/**
 * The recorder our own bot is started through — chosen by {@code lightmove.notetaker.runner}; a test's recording
 * one replaces it — and the platforms it joins.
 */
@Configuration(proxyBeanMethods = false)
public class NotetakerConfig {

    static final String CLOUD_RUN_BASE_URL = "https://run.googleapis.com";

    @Bean
    @Fallback
    MeetingRecorder ownMeetingRecorder(LightMoveProperties properties) {
        return switch (properties.notetaker().runner()) {
            case "log" -> new LogMeetingRecorder();
            case "local" -> new LocalProcessMeetingRecorder(properties, repositoryRoot());
            case "cloud-run-job" -> new CloudRunJobMeetingRecorder(properties, applicationDefaultTokens(),
                    RestClient.builder(), CLOUD_RUN_BASE_URL);
            default -> throw new IllegalStateException(
                    "lightmove.notetaker.runner must be log, local or cloud-run-job, not " + properties.notetaker().runner());
        };
    }

    @Bean
    OfferedPlatforms offeredNotetakerPlatforms(LightMoveProperties properties) {
        return OfferedPlatforms.parse(properties.notetaker().platforms());
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
