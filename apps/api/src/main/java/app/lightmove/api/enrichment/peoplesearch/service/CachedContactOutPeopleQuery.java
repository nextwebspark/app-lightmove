package app.lightmove.api.enrichment.peoplesearch.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.service.CachedPeopleStore;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleClient;
import app.lightmove.api.enrichment.common.model.ContactOutCount;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleIndex;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleRecords;
import app.lightmove.api.enrichment.common.service.PeopleQueryKeys;
import app.lightmove.api.enrichment.peoplesearch.model.PeoplePage;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * ContactOut's People Search asked people-first, through the V87 cache so nothing is bought twice: a page
 * already asked inside {@code people-cache-ttl} is answered from {@code app_lm_vendor_people_search} and
 * {@code app_lm_vendor_person} with no vendor call, for any workspace, and every page bought is kept. The
 * key hashes the whole body and the page, so page two of a question is its own row, and a page whose
 * people have aged out is asked again rather than answered short.
 *
 * <p>Counts are free but rate-limited, so they are held briefly in memory only — a count is not a
 * third party's record, and a stale one costs nothing but a slightly wrong number.
 */
@Service
public class CachedContactOutPeopleQuery {

    static final String PROVIDER = "contactout";

    private static final Duration COUNT_TTL = Duration.ofMinutes(10);

    private final ContactOutPeopleIndex client;
    private final CachedPeopleStore store;
    private final ObjectMapper json;
    private final Duration ttl;
    private final Cache<String, ContactOutCount> counts;

    public CachedContactOutPeopleQuery(ContactOutPeopleIndex client, CachedPeopleStore store, ObjectMapper json,
                                       LightMoveProperties properties) {
        this.client = client;
        this.store = store;
        this.json = json;
        this.ttl = properties.enrichment().peopleCacheTtl();
        this.counts = Caffeine.newBuilder().expireAfterWrite(COUNT_TTL).maximumSize(10_000).build();
    }

    public boolean isOffered() {
        return client.isOffered();
    }

    public ContactOutCount count(Map<String, Object> body) {
        return counts.get(canonical(body), ignored -> client.count(body));
    }

    /** The page as an earlier search bought it, while every person on it may still be read. */
    public Optional<PeoplePage> cachedPage(Map<String, Object> body, int page) {
        return store.answerTo(queryKeyOf(body, page), freshAfter())
                .map(asked -> new PeoplePage(asked.hits(), totalOf(asked), 0, asked.hits().size()));
    }

    /**
     * Asks ContactOut with {@code sent}, keeps every profile it billed, and files the page under
     * {@code question} — the body less what only narrows it for one mandate, so declining a company
     * does not turn a page already paid for into a new question.
     */
    public PeoplePage buyPage(Map<String, Object> question, Map<String, Object> sent, int page) {
        BrightDataPeopleHits bought = ContactOutPeopleRecords.toHits(
                client.search(sent, page, ContactOutPeopleClient.MAX_PAGE_SIZE), json);
        store.purgeFetchedBefore(freshAfter());
        store.rememberAll(PROVIDER, bought);
        store.rememberSearch(queryKeyOf(question, page), null,
                bought.hits().stream().map(BrightDataPerson::linkedinId).toList(), bought.totalHits());
        return new PeoplePage(bought.hits(), totalOf(bought), bought.hits().size(), 0);
    }

    /** A person a search returned, while their record may still be read; nothing is bought to read it. */
    public Optional<BrightDataPerson> onFile(String linkedinSlug) {
        return store.find(linkedinSlug, freshAfter());
    }

    /** ContactOut's own record of each person, where the cache kept one, by slug. */
    public Map<String, String> sourceRecordsOf(Collection<String> linkedinSlugs) {
        return store.sourceRecordsOf(linkedinSlugs, freshAfter());
    }

    private String queryKeyOf(Map<String, Object> body, int page) {
        return PeopleQueryKeys.of(String.join("|", PROVIDER, "people", canonical(body), "page=" + page,
                "size=" + ContactOutPeopleClient.MAX_PAGE_SIZE));
    }

    private Instant freshAfter() {
        return Instant.now().minus(ttl);
    }

    /** The body with its keys and every list sorted, so one question in any order is one key. */
    private String canonical(Map<String, Object> body) {
        return json.writeValueAsString(sorted(body));
    }

    private Object sorted(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> ordered = new TreeMap<>();
            map.forEach((key, entry) -> ordered.put(String.valueOf(key), sorted(entry)));
            return ordered;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::sorted)
                    .sorted(Comparator.comparing(json::writeValueAsString))
                    .toList();
        }
        return value;
    }

    private static long totalOf(BrightDataPeopleHits hits) {
        return hits.totalHits() == null ? hits.hits().size() : hits.totalHits();
    }
}
