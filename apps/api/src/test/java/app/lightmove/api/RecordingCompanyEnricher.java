package app.lightmove.api;

import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
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
    private final List<String> searched = new CopyOnWriteArrayList<>();
    private volatile VendorCompanyRecord answer;
    private volatile List<VendorCompanyRecord> searchAnswer = List.of();

    @Override
    public Optional<VendorCompanyRecord> fetch(String linkedinSlug) {
        fetched.add(linkedinSlug);
        return Optional.ofNullable(answer);
    }

    @Override
    public List<VendorCompanyRecord> searchByName(String namePart, String countryCode) {
        searched.add(namePart);
        return searchAnswer;
    }

    @Override
    public String provider() {
        return "recording";
    }

    public void answerWith(VendorCompanyRecord record) {
        this.answer = record;
    }

    public void answerSearchWith(List<VendorCompanyRecord> hits) {
        this.searchAnswer = List.copyOf(hits);
    }

    public void clear() {
        fetched.clear();
        searched.clear();
        answer = null;
        searchAnswer = List.of();
    }

    public List<String> searchedNames() {
        return List.copyOf(searched);
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
