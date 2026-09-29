package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.core.config.BrightDataSettings;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.core.resilience.service.VendorRetryPredicate;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.common.service.BrightDataSearch;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Bright Data's LinkedIn people dataset — an indexed lookup keyed on {@code linkedin_id} (the
 * {@code url} field is analyzed and matches nothing). Masked values map to null; a record whose career
 * maps away is a thin answer {@code FallbackProfileEnricher} treats as a miss. The people cache (V87)
 * is read first and written after, so a person a Find executives run already bought costs a capture
 * nothing, and a person captured once is free to every later run.
 */
@Slf4j
public class BrightDataProfileEnricher implements LinkedInProfileEnricher {

    private static final String VENDOR = "brightdata";

    private final RestClient client;
    private final String datasetId;
    private final VendorCallGuard guard;
    private final ProfilePhotoDownloader photos;
    private final CachedPeopleStore people;
    private final Duration peopleCacheTtl;
    private final ObjectMapper json;

    public BrightDataProfileEnricher(BrightDataSettings config, VendorClientFactory clientFactory,
                                     VendorRateLimiter rateLimiter, VendorCallGuard guard,
                                     ProfilePhotoDownloader photos, CachedPeopleStore people,
                                     Duration peopleCacheTtl, RestClient.Builder builder, ObjectMapper json) {
        this.json = json;
        this.guard = guard;
        this.photos = photos;
        this.people = people;
        this.peopleCacheTtl = peopleCacheTtl;
        this.datasetId = config.datasetId();
        this.client = clientFactory.create(VendorClientSpec.bearer(VENDOR, config.baseUrl(),
                config.apiKey(), config.readTimeout(), config.requestsPerSecond()), builder, rateLimiter);
    }

    @Override
    @Retryable(
            predicate = VendorRetryPredicate.class,
            maxRetriesString = "${lightmove.enrichment.brightdata.max-retries}",
            delayString = "${lightmove.resilience.retry-delay}",
            jitterString = "${lightmove.resilience.retry-jitter}",
            multiplierString = "${lightmove.resilience.retry-multiplier}",
            maxDelayString = "${lightmove.resilience.retry-max-delay}")
    public Optional<EnrichedProfile> fetch(String linkedinUrl) {
        // The dataset keys on the lower-case slug exactly, while LinkedIn treats case as one page.
        String slug = LinkedInUrls.profileSlugOrNull(linkedinUrl);
        if (slug == null) {
            return Optional.empty();
        }
        Optional<BrightDataPerson> onFile = people.find(slug, Instant.now().minus(peopleCacheTtl));
        if (onFile.isPresent()) {
            return Optional.of(withPhoto(onFile.get()));
        }
        BrightDataPeopleHits result = BrightDataPeopleHits.read(guard.call(VendorCall.of(VENDOR, "profile-search"),
                () -> client.post()
                        .uri("/datasets/search/{datasetId}", datasetId)
                        .body(BrightDataSearch.exactlyOneWhere("linkedin_id", slug))
                        .retrieve()
                        .body(JsonNode.class)), json);

        if (result.hits().isEmpty()) {
            log.debug("Bright Data dataset holds no record for {}", slug);
            return Optional.empty();
        }
        people.rememberAll(VENDOR, result);
        return Optional.of(withPhoto(result.hits().getFirst()));
    }

    private EnrichedProfile withPhoto(BrightDataPerson person) {
        return BrightDataPersonProfiles.toEnrichedProfile(person)
                .withPhoto(photos.fetchOrNull(person.usableAvatarUrl()));
    }
}
