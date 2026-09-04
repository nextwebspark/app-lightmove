package app.lightmove.api;

import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import app.lightmove.api.enrichment.company.service.LinkedInCompanyEnricher;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A {@link LinkedInCompanyEnricher} that answers whatever the test scripted and remembers what it
 * was asked — the company-side twin of {@link RecordingProfileEnricher}.
 */
public class RecordingCompanyEnricher implements LinkedInCompanyEnricher {

    private final List<String> fetched = new CopyOnWriteArrayList<>();
    private volatile CapturedCompanyDetails answer;

    @Override
    public Optional<CapturedCompanyDetails> fetch(String linkedinSlug) {
        fetched.add(linkedinSlug);
        return Optional.ofNullable(answer);
    }

    public void answerWith(CapturedCompanyDetails details) {
        this.answer = details;
    }

    public void clear() {
        fetched.clear();
        answer = null;
    }

    public List<String> fetchedSlugs() {
        return List.copyOf(fetched);
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        public RecordingCompanyEnricher recordingCompanyEnricher() {
            return new RecordingCompanyEnricher();
        }
    }
}
