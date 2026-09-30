package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.service.CachedPeopleStore;
import app.lightmove.api.enrichment.common.service.BrightDataSearch;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * The people search with memory, so nobody is bought twice (V87). A question already asked inside
 * {@code people-cache-ttl} is answered from the cache with no vendor call. Otherwise the people on
 * file at that employer whose title fits are read back free, and the vendor is asked only for the
 * rest — with every person on file there excluded ({@code linkedin_id not_in}), since a record the
 * vendor returns is billed whether or not it was new to us.
 *
 * <p>The exclusion is everyone on file at the employer, never a mandate's own mapped people: the
 * search's answer is remembered for every workspace, and one firm's roster must not shape it.
 *
 * <p>Everything read back is the asked provider's own, so a stale dataset record never answers a search
 * of a fresher index.
 */
@Service
public class CachedPeopleSearch {

    /** Read back per employer at most; the vendor's exclusion list holds a thousand. */
    private static final int MAX_ON_FILE = BrightDataSearch.MAX_EXCLUDED;

    private final CachedPeopleStore store;
    private final Duration ttl;

    public CachedPeopleSearch(CachedPeopleStore store, LightMoveProperties properties) {
        this.store = store;
        this.ttl = properties.enrichment().peopleCacheTtl();
    }

    /** One provider, cache first; one person appears once, since ContactOut cannot exclude those read back. */
    public PeopleFound currentEmployeesTitled(PeopleSearch vendor, SearchedEmployer employer, SourcingSpec spec,
                                              Seniority seat, List<String> countryCodes, int size) {
        String companySlug = employer.linkedinSlug();
        Instant freshAfter = Instant.now().minus(ttl);
        String queryKey = queryKeyOf(vendor.provider(), companySlug, spec, seat, countryCodes, size);
        Optional<BrightDataPeopleHits> asked = store.answerTo(queryKey, freshAfter);
        if (asked.isPresent()) {
            List<BrightDataPerson> people = asked.get().hits();
            return new PeopleFound(people, 0, people.size(), asked.get().totalHits(), vendor.provider(),
                    vendor.researchedBy());
        }

        List<BrightDataPerson> onFile = store.atCompany(companySlug, vendor.provider(), freshAfter, MAX_ON_FILE);
        List<BrightDataPerson> fittingOnFile = onFile.stream()
                .filter(person -> fits(person, spec, countryCodes))
                .toList();
        List<BrightDataPerson> fitting = fittingOnFile.stream().limit(size).toList();
        List<BrightDataPerson> bought = List.of();
        Long matched = (long) fittingOnFile.size();
        int stillWanted = size - fitting.size();
        if (stillWanted > 0) {
            List<String> excluded = onFile.stream().map(BrightDataPerson::linkedinId).toList();
            BrightDataPeopleHits answered = vendor.currentEmployeesTitled(employer, spec, seat, countryCodes,
                    excluded, stillWanted);
            bought = answered.hits();
            matched = answered.totalHits() == null ? null : matched + answered.totalHits();
            store.purgeFetchedBefore(freshAfter);
            store.rememberAll(vendor.provider(), answered);
        }
        Set<String> seen = new HashSet<>();
        List<BrightDataPerson> answer = Stream.concat(fitting.stream(), bought.stream())
                .filter(person -> person.linkedinId() == null
                        || seen.add(person.linkedinId().toLowerCase(Locale.ROOT)))
                .toList();
        store.rememberSearch(queryKey, companySlug, answer.stream()
                .map(BrightDataPerson::linkedinId)
                .filter(slug -> slug != null && !slug.isBlank())
                .toList(), matched);
        return new PeopleFound(answer, bought.size(), fitting.size(), matched, vendor.provider(),
                vendor.researchedBy());
    }

    /**
     * The vendor's rule, read locally over a record on file: the title holds one seniority word, one
     * function word when there are any, and no excluded word — case-insensitive substrings, which is
     * what {@code includes} matches on a single word — and the residence is one of {@code countryCodes}
     * when any are given.
     */
    static boolean fits(BrightDataPerson person, SourcingSpec spec, List<String> countryCodes) {
        String title = person.position() == null ? "" : person.position().toLowerCase(Locale.ROOT);
        boolean senior = spec.seniorityWords().isEmpty() || containsAny(title, spec.seniorityWords());
        boolean function = spec.functionWords().isEmpty() || containsAny(title, spec.functionWords());
        boolean excluded = containsAny(title, spec.excludedWords());
        boolean placed = countryCodes.isEmpty() || (person.countryCode() != null
                && countryCodes.stream().anyMatch(code -> code.equalsIgnoreCase(person.countryCode())));
        return !title.isEmpty() && senior && function && !excluded && placed;
    }

    /**
     * The same question in any word order, spelt in any case, to the same provider, for the same seat, is
     * the same key — the seat decides which titles a provider asks first.
     */
    static String queryKeyOf(String provider, String companySlug, SourcingSpec spec, Seniority seat,
                             List<String> countryCodes, int size) {
        String canonical = String.join("|", provider.toLowerCase(Locale.ROOT), companySlug.toLowerCase(Locale.ROOT),
                seat == null ? "-" : seat.name(),
                sortedLower(spec.seniorityWords()), sortedLower(spec.functionWords()),
                "-" + sortedLower(spec.excludedWords()), sortedLower(countryCodes), Integer.toString(size));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean containsAny(String title, List<String> words) {
        return words.stream().anyMatch(word -> title.contains(word.toLowerCase(Locale.ROOT)));
    }

    private static String sortedLower(List<String> values) {
        List<String> sorted = new ArrayList<>(values.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList());
        sorted.sort(null);
        return String.join(",", sorted);
    }
}
