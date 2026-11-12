package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import app.lightmove.api.enrichment.sourcing.model.TitleTier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A {@link LiveProfiles} over the people a test scripted per company slug, matched as the live index
 * matches titles — whole words of the current role — and remembering every call, so a run goes end to end
 * with no vendor and a test can count what was bought and what was checked free.
 */
public class RecordingLiveProfiles implements LiveProfiles {

    public record Searched(String companySlug, TitleTier tier, List<String> countryCodes, int size) {}

    private final Map<String, List<BrightDataPerson>> people = new ConcurrentHashMap<>();
    private final List<Searched> searches = new CopyOnWriteArrayList<>();
    private final List<String> titleChecks = new CopyOnWriteArrayList<>();
    private final List<String> profilesBought = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failure;
    private volatile boolean offered = true;

    @Override
    public boolean isOffered() {
        return offered;
    }

    @Override
    public String provider() {
        return "contactout";
    }

    @Override
    public EnrichmentVendor researchedBy() {
        return EnrichmentVendor.CONTACTOUT;
    }

    @Override
    public long count(SearchedEmployer employer, TitleTier tier, List<String> countryCodes) {
        failIfAsked();
        return holding(employer, tier, countryCodes).count();
    }

    @Override
    public long countAcross(List<SearchedEmployer> employers, SearchWords words, List<String> countryCodes) {
        failIfAsked();
        return employers.stream().mapToLong(employer -> holding(employer, TitleTier.of(words), countryCodes).count())
                .sum();
    }

    @Override
    public BrightDataPeopleHits search(SearchedEmployer employer, TitleTier tier, List<String> countryCodes,
                                       int size) {
        searches.add(new Searched(employer.linkedinSlug(), tier, List.copyOf(countryCodes), size));
        failIfAsked();
        List<BrightDataPerson> found = holding(employer, tier, countryCodes).toList();
        return BrightDataPeopleHits.of(found.stream().limit(size).toList(), (long) found.size());
    }

    @Override
    public boolean holdsTitle(SearchedEmployer employer, String personName, SearchWords words) {
        titleChecks.add(personName);
        failIfAsked();
        return named(employer, personName, words).findAny().isPresent();
    }

    @Override
    public BrightDataPeopleHits profileOf(SearchedEmployer employer, String personName, SearchWords words) {
        failIfAsked();
        List<BrightDataPerson> found = named(employer, personName, words).limit(1).toList();
        found.forEach(person -> profilesBought.add(person.linkedinId()));
        return BrightDataPeopleHits.of(found, (long) found.size());
    }

    /** The live index's people at {@code companySlug}, each with the title they hold today. */
    public void answerWith(String companySlug, List<BrightDataPerson> peopleThere) {
        people.put(companySlug, List.copyOf(peopleThere));
    }

    public void failWith(RuntimeException exception) {
        this.failure = exception;
    }

    public void offer(boolean isOffered) {
        this.offered = isOffered;
    }

    public void clear() {
        people.clear();
        searches.clear();
        titleChecks.clear();
        profilesBought.clear();
        failure = null;
        offered = true;
    }

    /** Every billed tier search, in order. */
    public List<Searched> searches() {
        return List.copyOf(searches);
    }

    /** The names checked by a free count, in order. */
    public List<String> titleChecks() {
        return List.copyOf(titleChecks);
    }

    /** The slugs of the single profiles bought by name. */
    public List<String> profilesBought() {
        return List.copyOf(profilesBought);
    }

    private Stream<BrightDataPerson> holding(SearchedEmployer employer, TitleTier tier, List<String> countryCodes) {
        return at(employer)
                .filter(person -> TitleWords.holds(CurrentRole.of(person), tier))
                .filter(person -> PeopleCache.livesIn(person, countryCodes));
    }

    private Stream<BrightDataPerson> named(SearchedEmployer employer, String personName, SearchWords words) {
        return at(employer)
                .filter(person -> person.name().equalsIgnoreCase(personName))
                .filter(person -> TitleWords.holds(CurrentRole.of(person), words));
    }

    private Stream<BrightDataPerson> at(SearchedEmployer employer) {
        return people.getOrDefault(employer.linkedinSlug(), List.of()).stream();
    }

    private void failIfAsked() {
        if (failure != null) {
            throw failure;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /** {@code @Primary} so it wins over the ContactOut adapter the application wires. */
        @Bean
        @Primary
        public RecordingLiveProfiles recordingLiveProfiles() {
            return new RecordingLiveProfiles();
        }
    }
}
