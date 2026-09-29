package app.lightmove.api;

import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import app.lightmove.api.enrichment.sourcing.service.PeopleSearch;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A {@link PeopleSearch} that answers whatever the test scripted per company slug and remembers
 * every search it was asked — the people-side twin of {@link RecordingProfileEnricher}, so a Find
 * executives run goes end to end with no vendor, no network and no spend.
 */
public class RecordingPeopleSearch implements PeopleSearch {

    /** One search as it was asked: the slug, the words, the country codes and who it left out. */
    public record Asked(String companySlug, List<String> seniorityWords, List<String> functionWords,
                        List<String> excludedWords, List<String> countryCodes, List<String> excludedSlugs,
                        int size) {}

    private final Map<String, List<BrightDataPerson>> answers = new ConcurrentHashMap<>();
    private final List<Asked> asked = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failure;
    private volatile boolean offered = true;

    @Override
    public BrightDataPeopleHits currentEmployeesTitled(String companySlug, SourcingSpec spec,
                                                       List<String> countryCodes, List<String> excludedSlugs,
                                                       int size) {
        asked.add(new Asked(companySlug, spec.seniorityWords(), spec.functionWords(), spec.excludedWords(),
                countryCodes, List.copyOf(excludedSlugs), size));
        if (failure != null) {
            throw failure;
        }
        List<BrightDataPerson> matching = answers.getOrDefault(companySlug, List.of()).stream()
                .filter(person -> !excludedSlugs.contains(person.linkedinId()))
                .toList();
        return BrightDataPeopleHits.of(matching.stream().limit(size).toList(), (long) matching.size());
    }

    @Override
    public String provider() {
        return "recording";
    }

    @Override
    public boolean isOffered() {
        return offered;
    }

    public void answerWith(String companySlug, List<BrightDataPerson> people) {
        answers.put(companySlug, people);
    }

    public void failWith(RuntimeException exception) {
        this.failure = exception;
    }

    public void offer(boolean isOffered) {
        this.offered = isOffered;
    }

    public void clear() {
        answers.clear();
        asked.clear();
        failure = null;
        offered = true;
    }

    public List<Asked> searches() {
        return List.copyOf(asked);
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /** {@code @Primary} so it wins over the LogPeopleSearch the application would pick. */
        @Bean
        @Primary
        public RecordingPeopleSearch recordingPeopleSearch() {
            return new RecordingPeopleSearch();
        }
    }
}
