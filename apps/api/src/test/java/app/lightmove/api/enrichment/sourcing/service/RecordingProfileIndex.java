package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.service.CachedPeopleStore;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A {@link ProfileIndex} answering the records a test scripted per company slug — titled or not, as the
 * index holds them — and remembering every search it was asked.
 */
public class RecordingProfileIndex implements ProfileIndex {

    public record Searched(String companySlug, List<String> roleWords, List<String> countryCodes, int limit) {}

    private final Map<String, List<BrightDataPerson>> records = new ConcurrentHashMap<>();
    private final List<Searched> searches = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failure;
    private volatile boolean offered = true;

    @Override
    public boolean isOffered() {
        return offered;
    }

    @Override
    public String provider() {
        return CachedPeopleStore.PROFILE_INDEX_PROVIDER;
    }

    @Override
    public EnrichmentVendor researchedBy() {
        return EnrichmentVendor.BRIGHTDATA;
    }

    @Override
    public BrightDataPeopleHits whoWorksAt(SearchedEmployer employer, SearchWords words, List<String> countryCodes,
                                           List<String> excludedSlugs, int limit) {
        searches.add(new Searched(employer.linkedinSlug(), words.roleWords(), List.copyOf(countryCodes), limit));
        if (failure != null) {
            throw failure;
        }
        List<BrightDataPerson> found = records.getOrDefault(employer.linkedinSlug(), List.of()).stream()
                .filter(person -> PeopleCache.livesIn(person, countryCodes))
                .filter(person -> !excludedSlugs.contains(person.linkedinId()))
                .toList();
        return BrightDataPeopleHits.of(found.stream().limit(limit).toList(), (long) found.size());
    }

    /** The index's records of people at {@code companySlug}. */
    public void answerWith(String companySlug, List<BrightDataPerson> peopleThere) {
        records.put(companySlug, List.copyOf(peopleThere));
    }

    public void failWith(RuntimeException exception) {
        this.failure = exception;
    }

    public void offer(boolean isOffered) {
        this.offered = isOffered;
    }

    public void clear() {
        records.clear();
        searches.clear();
        failure = null;
        offered = true;
    }

    public List<Searched> searches() {
        return List.copyOf(searches);
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /** {@code @Primary} so it wins over the Business Search adapter the application wires. */
        @Bean
        @Primary
        public RecordingProfileIndex recordingProfileIndex() {
            return new RecordingProfileIndex();
        }
    }
}
