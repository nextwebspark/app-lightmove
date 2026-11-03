package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.core.config.BrightDataSettings;
import app.lightmove.api.core.config.BusinessSearchSettings;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.candidate.service.CachedPeopleStore;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Bright Data's Business Search ({@code POST /search/people}) as the profile index: one structured query a
 * company, billed per search whatever it returns. {@code current_company_id} takes the company's LinkedIn
 * slug though the docs list it as output only, and {@code text} matches whole words, so "CTO" is never
 * found inside "Director". The {@code role} group is the title, past titles and About together: the index
 * has dropped most current titles, so a title alone finds few.
 */
public class BusinessSearchProfileIndex implements ProfileIndex {

    static final String VENDOR = CachedPeopleStore.PROFILE_INDEX_PROVIDER;

    /** Results come back in pages of a hundred; a search asks for one page at most. */
    private static final int MAX_LIMIT = 100;

    private final RestClient client;
    private final VendorCallGuard guard;
    private final ObjectMapper json;
    private final Duration permitWait;
    private final Clock clock;

    /** Paced before the client is built, since the first pacing a vendor gets is the one that holds. */
    public BusinessSearchProfileIndex(BrightDataSettings brightData, BusinessSearchSettings settings,
                                      VendorClientFactory clientFactory, VendorRateLimiter rateLimiter,
                                      VendorCallGuard guard, ObjectMapper json, Clock clock) {
        rateLimiter.pace(VENDOR, 1, settings.minInterval());
        this.client = clientFactory.create(VendorClientSpec.bearer(VENDOR, brightData.baseUrl(), brightData.apiKey(),
                settings.readTimeout(), 1), RestClient.builder(), rateLimiter);
        this.guard = guard;
        this.json = json;
        this.permitWait = settings.permitWait();
        this.clock = clock;
    }

    @Override
    public boolean isOffered() {
        return true;
    }

    @Override
    public String provider() {
        return VENDOR;
    }

    @Override
    public EnrichmentVendor researchedBy() {
        return EnrichmentVendor.BRIGHTDATA;
    }

    @Override
    public BrightDataPeopleHits whoWorksAt(SearchedEmployer employer, SearchWords words, List<String> countryCodes,
                                           List<String> excludedSlugs, int limit) {
        if (employer.linkedinSlug() == null || employer.linkedinSlug().isBlank() || words.roleWords().isEmpty()) {
            return BrightDataPeopleHits.of(List.of(), 0L);
        }
        Map<String, Object> body = searchBody(employer.linkedinSlug(), words, countryCodes, excludedSlugs, limit);
        JsonNode answer = guard.call(VendorCall.of(VENDOR, "people-search").waitingUpTo(permitWait),
                () -> client.post()
                        .uri("/search/people")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class));
        return BusinessSearchRecords.toHits(answer, YearMonth.now(clock), json);
    }

    /**
     * People at the company whose title, past titles or About hold any of the words' role words, living in
     * {@code countryCodes} when any are given and not among {@code excludedSlugs}.
     */
    static Map<String, Object> searchBody(String companySlug, SearchWords words, List<String> countryCodes,
                                          List<String> excludedSlugs, int limit) {
        List<Map<String, Object>> conditions = new ArrayList<>();
        conditions.add(Map.of("equals", Map.of("current_company_id", companySlug)));
        conditions.add(Map.of("text", Map.of("role", Map.of("value", String.join(" ", words.roleWords()),
                "mode", "any"))));
        if (!countryCodes.isEmpty()) {
            conditions.add(Map.of("in", Map.of("country_code", List.copyOf(countryCodes))));
        }
        if (!excludedSlugs.isEmpty()) {
            conditions.add(Map.of("not", Map.of("in", Map.of("linkedin_person_id", List.copyOf(excludedSlugs)))));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", "ludicrous");
        body.put("query", Map.of("and", conditions));
        body.put("limit", Math.min(Math.max(limit, 1), MAX_LIMIT));
        body.put("view", "full");
        return body;
    }
}
