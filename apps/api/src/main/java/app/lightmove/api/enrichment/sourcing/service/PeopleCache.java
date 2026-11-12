package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.service.CachedPeopleStore;
import app.lightmove.api.enrichment.common.service.PeopleQueryKeys;
import app.lightmove.api.enrichment.common.service.SearchHitFiling;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import app.lightmove.api.enrichment.sourcing.model.TitleTier;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * The searches with memory (V87), so nobody is bought twice and a question asked again inside
 * {@code people-cache-ttl} costs nothing. The answer is shared by every workspace, so nothing a mandate
 * holds shapes it; the caller leaves out its own people.
 */
@Service
public class PeopleCache {

    /** People read back per employer at most. */
    private static final int MAX_ON_FILE = 1_000;

    private final CachedPeopleStore store;
    private final Duration ttl;

    public PeopleCache(CachedPeopleStore store, LightMoveProperties properties) {
        this.store = store;
        this.ttl = properties.enrichment().peopleCacheTtl();
    }

    /**
     * Live profiles for one tier. The people on file at the employer whose title the tier finds are read
     * back free, and only the rest are bought; ContactOut cannot leave the former out, so one it returns
     * again is counted once. Every profile bought is kept, those living elsewhere too, since each was paid
     * for; the answer holds those living in {@code countryCodes}.
     */
    PeopleFound live(LiveProfiles live, SearchedEmployer employer, TitleTier tier, List<String> countryCodes,
                     int size) {
        Instant freshAfter = Instant.now().minus(ttl);
        String key = queryKey(live.provider(), employer.linkedinSlug(), tier.key(), countryCodes, size);
        Optional<BrightDataPeopleHits> asked = store.answerTo(key, freshAfter);
        if (asked.isPresent()) {
            return new PeopleFound(asked.get().hits(), 0, asked.get().hits().size(), false);
        }
        List<BrightDataPerson> fitting = store.atCompany(employer.linkedinSlug(), live.provider(), freshAfter,
                        MAX_ON_FILE).stream()
                .filter(person -> TitleWords.holds(CurrentRole.of(person), tier))
                .filter(person -> livesIn(person, countryCodes))
                .limit(size)
                .toList();
        List<BrightDataPerson> bought = List.of();
        int wanted = size - fitting.size();
        if (wanted > 0) {
            BrightDataPeopleHits answered = live.search(employer, tier, countryCodes, wanted);
            store.purgeFetchedBefore(freshAfter);
            store.rememberAll(live.provider(), answered);
            bought = answered.hits();
        }
        List<BrightDataPerson> answer = distinct(Stream.concat(fitting.stream(),
                bought.stream().filter(person -> livesIn(person, countryCodes))).toList());
        store.rememberSearch(key, employer.linkedinSlug(), slugsOf(answer), null);
        return new PeopleFound(answer, bought.size(), fitting.size(), wanted > 0);
    }

    /** One profile-index search, answered from the cache when the same question was asked inside the TTL. */
    PeopleFound index(ProfileIndex index, SearchedEmployer employer, SearchWords words, List<String> countryCodes,
                      int limit) {
        Instant freshAfter = Instant.now().minus(ttl);
        String key = queryKey(index.provider(), employer.linkedinSlug(), words.key(), countryCodes, limit);
        Optional<BrightDataPeopleHits> asked = store.answerTo(key, freshAfter);
        if (asked.isPresent()) {
            return new PeopleFound(asked.get().hits(), 0, asked.get().hits().size(), false);
        }
        BrightDataPeopleHits answered = index.whoWorksAt(employer, words, countryCodes, List.of(), limit);
        store.purgeFetchedBefore(freshAfter);
        store.rememberAll(index.provider(), answered);
        List<BrightDataPerson> answer = distinct(answered.hits());
        store.rememberSearch(key, employer.linkedinSlug(), slugsOf(answer), answered.totalHits());
        return new PeopleFound(answer, 0, 0, true);
    }

    /** Free: the people the live index answered before, at the employer, whose title the words find, living in the countries. */
    long onFile(LiveProfiles live, SearchedEmployer employer, SearchWords words, List<String> countryCodes) {
        if (employer.linkedinSlug() == null) {
            return 0;
        }
        return store.atCompany(employer.linkedinSlug(), live.provider(), Instant.now().minus(ttl), MAX_ON_FILE).stream()
                .filter(person -> TitleWords.holds(CurrentRole.of(person), words))
                .filter(person -> livesIn(person, countryCodes))
                .count();
    }

    /**
     * The live profile of someone a free check confirmed: the one on file while fresh, else — when
     * {@code mayBuy} — bought by name and kept like every profile bought. Only the person the index named is
     * answered.
     */
    PeopleFound liveProfileOf(LiveProfiles live, SearchedEmployer employer, BrightDataPerson named,
                              SearchWords words, boolean mayBuy) {
        Instant freshAfter = Instant.now().minus(ttl);
        Optional<BrightDataPerson> onFile = store.find(named.linkedinId(), live.provider(), freshAfter);
        if (onFile.isPresent()) {
            return new PeopleFound(List.of(onFile.get()), 0, 1, false);
        }
        if (!mayBuy) {
            return new PeopleFound(List.of(), 0, 0, false);
        }
        BrightDataPeopleHits answered = live.profileOf(employer, SearchHitFiling.nameOf(named), words);
        store.purgeFetchedBefore(freshAfter);
        store.rememberAll(live.provider(), answered);
        List<BrightDataPerson> same = answered.hits().stream()
                .filter(person -> named.linkedinId().equalsIgnoreCase(person.linkedinId()))
                .toList();
        return new PeopleFound(same, answered.hits().size(), 0, true);
    }

    /** A person whose residence is unknown is kept: the vendors leave it out as often as they get it wrong. */
    static boolean livesIn(BrightDataPerson person, List<String> countryCodes) {
        return countryCodes.isEmpty() || person.countryCode() == null
                || countryCodes.stream().anyMatch(code -> code.equalsIgnoreCase(person.countryCode()));
    }

    /** The same question in any word order or case, to the same provider about the same employer, is one key. */
    static String queryKey(String provider, String companySlug, String wordsKey, List<String> countryCodes,
                           int size) {
        List<String> countries = new ArrayList<>(countryCodes.stream()
                .map(code -> code.toUpperCase(Locale.ROOT))
                .toList());
        countries.sort(null);
        return PeopleQueryKeys.of(String.join("|", provider.toLowerCase(Locale.ROOT),
                companySlug.toLowerCase(Locale.ROOT), wordsKey, String.join(",", countries), Integer.toString(size)));
    }

    private static List<BrightDataPerson> distinct(List<BrightDataPerson> people) {
        Set<String> seen = new HashSet<>();
        return people.stream()
                .filter(person -> person.linkedinId() != null && !person.linkedinId().isBlank())
                .filter(person -> seen.add(person.linkedinId().toLowerCase(Locale.ROOT)))
                .toList();
    }

    private static List<String> slugsOf(List<BrightDataPerson> people) {
        return people.stream().map(BrightDataPerson::linkedinId).toList();
    }
}
