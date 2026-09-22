package app.lightmove.api;

import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import app.lightmove.api.enrichment.company.service.LinkedInCompanyEnricher;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<String, VendorCompanyRecord> bySlug = new ConcurrentHashMap<>();
    private volatile VendorCompanyRecord answer;

    @Override
    public Optional<VendorCompanyRecord> fetch(String linkedinSlug) {
        fetched.add(linkedinSlug);
        VendorCompanyRecord scripted = bySlug.get(linkedinSlug);
        return Optional.ofNullable(scripted != null ? scripted : answer);
    }

    @Override
    public String provider() {
        return "recording";
    }

    /** The answer to every slug. Right for a test asking about one company. */
    public void answerWith(VendorCompanyRecord record) {
        this.answer = record;
    }

    /**
     * The answer to <i>one</i> slug, and nothing for any other.
     *
     * <p>Beside {@link #answerWith} rather than replacing it, because a resolver test needs to say
     * "the vendor holds this page and not that one" — and a double answering the same record to
     * every slug silently resolves the row that was supposed to come back empty.
     */
    public void answerFor(String linkedinSlug, VendorCompanyRecord record) {
        bySlug.put(linkedinSlug, record);
    }

    public void clear() {
        fetched.clear();
        bySlug.clear();
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
