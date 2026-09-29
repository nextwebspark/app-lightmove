package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.core.config.BrightDataSettings;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.core.resilience.service.VendorRetryPredicate;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import app.lightmove.api.enrichment.common.service.BrightDataSearch;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The people dataset searched by employer and title through the synchronous Search API — the same
 * endpoint {@code BrightDataProfileEnricher} looks one profile up on, asked a different question.
 * The filter is {@link BrightDataSearch#currentEmployeesTitled}'s, and its two traps are documented there.
 */
@Slf4j
public class BrightDataPeopleSearch implements PeopleSearch {

    private static final String VENDOR = "brightdata";

    private final RestClient client;
    private final String datasetId;
    private final VendorCallGuard guard;
    private final ObjectMapper json;

    public BrightDataPeopleSearch(BrightDataSettings config, VendorClientFactory clientFactory,
                                  VendorRateLimiter rateLimiter, VendorCallGuard guard, RestClient.Builder builder,
                                  ObjectMapper json) {
        this.guard = guard;
        this.json = json;
        this.datasetId = config.datasetId();
        this.client = clientFactory.create(VendorClientSpec.bearer(VENDOR, config.baseUrl(),
                config.apiKey(), config.readTimeout(), config.requestsPerSecond()), builder, rateLimiter);
    }

    @Override
    public String provider() {
        return VENDOR;
    }

    @Override
    @Retryable(
            predicate = VendorRetryPredicate.class,
            maxRetriesString = "${lightmove.enrichment.brightdata.max-retries}",
            delayString = "${lightmove.resilience.retry-delay}",
            jitterString = "${lightmove.resilience.retry-jitter}",
            multiplierString = "${lightmove.resilience.retry-multiplier}",
            maxDelayString = "${lightmove.resilience.retry-max-delay}")
    public BrightDataPeopleHits currentEmployeesTitled(String companySlug, SourcingSpec spec,
                                                       List<String> countryCodes, List<String> excludedSlugs,
                                                       int size) {
        BrightDataPeopleHits result = BrightDataPeopleHits.read(guard.call(VendorCall.of(VENDOR, "people-search"),
                () -> client.post()
                        .uri("/datasets/search/{datasetId}", datasetId)
                        .body(BrightDataSearch.currentEmployeesTitled(companySlug, spec.seniorityWords(),
                                spec.functionWords(), spec.excludedWords(), countryCodes, excludedSlugs, size))
                        .retrieve()
                        .body(JsonNode.class)), json);
        log.debug("Bright Data people search at {} matched {} in all, returned {}", companySlug,
                result.totalHits(), result.hits().size());
        return result;
    }
}
