package app.lightmove.api;

import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.enrichment.candidate.service.LinkedInProfileEnricher;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A {@link LinkedInProfileEnricher} that answers whatever the test scripted and remembers what it was
 * asked — the payoff for the enricher being a port, exactly as {@link RecordingEmailSender} is for
 * mail: the capture-to-enrichment flow runs end to end with no vendor, no network and no spend.
 */
public class RecordingProfileEnricher implements LinkedInProfileEnricher {

    private final List<String> fetched = new CopyOnWriteArrayList<>();
    private volatile EnrichedProfile answer;
    private volatile RuntimeException failure;

    @Override
    public Optional<EnrichedProfile> fetch(String linkedinUrl) {
        fetched.add(linkedinUrl);
        if (failure != null) {
            throw failure;
        }
        return Optional.ofNullable(answer);
    }

    public void answerWith(EnrichedProfile profile) {
        this.answer = profile;
        this.failure = null;
    }

    public void failWith(RuntimeException exception) {
        this.failure = exception;
    }

    public void clear() {
        fetched.clear();
        answer = null;
        failure = null;
    }

    public List<String> fetchedUrls() {
        return List.copyOf(fetched);
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /** {@code @Primary} so it wins over the LogProfileEnricher the application would pick. */
        @Bean
        @Primary
        public RecordingProfileEnricher recordingProfileEnricher() {
            return new RecordingProfileEnricher();
        }
    }
}
