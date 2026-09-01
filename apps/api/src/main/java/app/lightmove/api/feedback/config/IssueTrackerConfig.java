package app.lightmove.api.feedback.config;

import app.lightmove.api.core.config.GitHubFeedbackSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.feedback.service.GitHubIssueTracker;
import app.lightmove.api.feedback.service.IssueTracker;
import app.lightmove.api.feedback.service.LoggingIssueTracker;
import java.net.http.HttpClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Picks the {@link IssueTracker} from config — the one place that knows where reports go.
 *
 * <p>Unlike {@code EmailSenderConfig}, a missing credential here does <b>not</b> fail startup. Mail is
 * load-bearing: an instance that boots and silently drops verification emails is worse than one that
 * refuses to boot. A UAT reporter is not — refusing to start a production deployment because nobody
 * has issued a GitHub token would be the tail wagging the dog.
 */
@Configuration
@Slf4j
public class IssueTrackerConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    IssueTracker issueTracker(LightMoveProperties properties) {
        GitHubFeedbackSettings github = properties.feedback().github();

        if (!github.publishable()) {
            log.info("No GitHub feedback credential — UAT reports will be logged, not filed.");
            return new LoggingIssueTracker();
        }

        log.info("UAT reports will be filed as issues in {}", github.repository());
        return new GitHubIssueTracker(github, RestClient.builder().requestFactory(timeoutBoundFactory()));
    }

    /**
     * Timeouts are the point of this factory. Without them a hung GitHub holds a request thread until
     * the socket gives up, and a tester waits on their bug report for as long as GitHub takes to fail.
     */
    private static JdkClientHttpRequestFactory timeoutBoundFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(GitHubIssueTracker.READ_TIMEOUT);
        return factory;
    }
}
