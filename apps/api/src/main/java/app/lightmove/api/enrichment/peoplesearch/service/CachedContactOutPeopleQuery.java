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
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * ContactOut's People Search through the V87 cache: a page is keyed on the whole body and its number and
 * bought at most once per TTL, for every workspace. Counts are free and held in memory for minutes only.
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
        return counts.get(PeopleQueryKeys.canonical(body, json), ignored -> client.count(body));
    }

    /** The page as an earlier search bought it, while every person on it may still be read. */
    public Optional<PeoplePage> cachedPage(Map<String, Object> body, int page) {
        return store.answerTo(queryKeyOf(body, page), freshAfter())
                .map(asked -> new PeoplePage(asked.hits(), totalOf(asked), 0, asked.hits().size()));
    }

    /** Asks ContactOut, keeps every profile it billed, and files the page under the question. */
    public PeoplePage buyPage(Map<String, Object> question, int page) {
        BrightDataPeopleHits bought = ContactOutPeopleRecords.toHits(
                client.search(question, page, ContactOutPeopleClient.MAX_PAGE_SIZE), json);
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
        return PeopleQueryKeys.of(String.join("|", PROVIDER, "people", PeopleQueryKeys.canonical(body, json), "page=" + page,
                "size=" + ContactOutPeopleClient.MAX_PAGE_SIZE));
    }

    private Instant freshAfter() {
        return Instant.now().minus(ttl);
    }

    private static long totalOf(BrightDataPeopleHits hits) {
        return hits.totalHits() == null ? hits.hits().size() : hits.totalHits();
    }
}
