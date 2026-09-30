package app.lightmove.api.enrichment.common.service;

import app.lightmove.api.core.config.ContactOutSettings;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.enrichment.common.model.ContactOutCount;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleRecords.ContactOutSearchAnswer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * ContactOut's People Search and People Count on one key. Search bills a credit per profile returned and
 * is capped at 60 a minute; count is free and capped at 1,000, so it is paced as a vendor of its own and
 * a live count never queues behind a Find executives run. Not {@code @Retryable}: a retried search is a
 * second bill.
 */
public class ContactOutPeopleClient implements ContactOutPeopleIndex {

    static final String SEARCH_VENDOR = "contactout-search";
    static final String COUNT_VENDOR = "contactout-count";

    public static final int MAX_PAGE_SIZE = 25;

    /** An indexed search on their side; probed at a second or two. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    private final RestClient searchClient;
    private final RestClient countClient;
    private final VendorCallGuard guard;

    public ContactOutPeopleClient(ContactOutSettings config, VendorClientFactory clientFactory,
                                  VendorRateLimiter rateLimiter, VendorCallGuard guard) {
        this.guard = guard;
        this.searchClient = clientFactory.create(VendorClientSpec.header(SEARCH_VENDOR, config.baseUrl(), "token",
                config.apiKey(), READ_TIMEOUT, config.searchRequestsPerSecond()), RestClient.builder(), rateLimiter);
        this.countClient = clientFactory.create(VendorClientSpec.header(COUNT_VENDOR, config.baseUrl(), "token",
                config.apiKey(), READ_TIMEOUT, config.countRequestsPerSecond()), RestClient.builder(), rateLimiter);
    }

    @Override
    public boolean isOffered() {
        return true;
    }

    @Override
    public ContactOutCount count(Map<String, Object> filter) {
        ContactOutCount answer = guard.call(VendorCall.of(COUNT_VENDOR, "people-count"), () -> countClient.post()
                .uri("/v1/people/count")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(filter)
                .retrieve()
                .body(ContactOutCount.class));
        return answer == null ? ContactOutCount.NONE : answer;
    }

    /** One page, contacts never revealed and the career in the detailed shape the profile is filed from. */
    @Override
    public ContactOutSearchAnswer search(Map<String, Object> filter, int page, int pageSize) {
        Map<String, Object> body = new LinkedHashMap<>(filter);
        body.put("page", page);
        body.put("page_size", Math.min(pageSize, MAX_PAGE_SIZE));
        body.put("detailed_experience", true);
        body.put("detailed_education", true);
        body.put("reveal_info", false);
        return guard.call(VendorCall.of(SEARCH_VENDOR, "people-search"), () -> searchClient.post()
                .uri("/v1/people/search")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ContactOutSearchAnswer.class));
    }
}
