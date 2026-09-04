package app.lightmove.api.enrichment.company.service;

import app.lightmove.api.core.config.BrightDataSettings;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.core.resilience.service.VendorRetryPredicate;
import app.lightmove.api.enrichment.common.service.BrightDataSearch;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Answers from the record Bright Data's LinkedIn company dataset already holds — the same sub-second
 * indexed lookup the person enrichment uses, against the companies dataset, whose slug field is
 * {@code id} (verified live; {@code linkedin_id} errors and {@code company_id} is LinkedIn's numeric
 * id).
 */
@Slf4j
public class BrightDataCompanyEnricher implements LinkedInCompanyEnricher {

    public static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private static final String VENDOR = "brightdata";

    private final RestClient client;
    private final String datasetId;
    private final VendorCallGuard guard;

    public BrightDataCompanyEnricher(BrightDataSettings config, VendorClientFactory clientFactory,
                                     VendorRateLimiter rateLimiter, VendorCallGuard guard,
                                     RestClient.Builder builder) {
        this.guard = guard;
        this.datasetId = config.companyDatasetId();
        this.client = clientFactory.create(VendorClientSpec.bearer(VENDOR, config.baseUrl(),
                config.apiKey(), READ_TIMEOUT, config.requestsPerSecond()), builder, rateLimiter);
    }

    @Override
    @Retryable(
            predicate = VendorRetryPredicate.class,
            maxRetriesString = "${lightmove.resilience.max-retries}",
            delayString = "${lightmove.resilience.retry-delay}",
            jitterString = "${lightmove.resilience.retry-jitter}",
            multiplierString = "${lightmove.resilience.retry-multiplier}",
            maxDelayString = "${lightmove.resilience.retry-max-delay}")
    public Optional<CapturedCompanyDetails> fetch(String linkedinSlug) {
        BrightDataCompanyResult result = guard.call(VendorCall.of(VENDOR, "company-search"),
                () -> client.post()
                        .uri("/datasets/search/{datasetId}", datasetId)
                        .body(BrightDataSearch.exactlyOneWhere("id", linkedinSlug))
                        .retrieve()
                        .body(BrightDataCompanyResult.class));

        if (result == null || result.hits() == null || result.hits().isEmpty()) {
            log.info("Bright Data company dataset holds no record for {}", linkedinSlug);
            return Optional.empty();
        }
        return toDetails(result.hits().getFirst());
    }

    static Optional<CapturedCompanyDetails> toDetails(BrightDataCompany company) {
        if (company.name() == null || company.name().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new CapturedCompanyDetails(
                company.name(),
                company.industries(),
                countryOf(company.countryCodesArray()),
                company.headquarters(),
                company.employeesInLinkedin(),
                null,
                company.website(),
                company.url(),
                company.founded(),
                company.about(),
                company.logo(),
                null,
                null));
    }

    /** The dataset speaks ISO-2 codes; the Country column speaks names, as the Apollo rows do. */
    private static String countryOf(List<String> countryCodes) {
        if (countryCodes == null || countryCodes.isEmpty()) {
            return null;
        }
        String code = countryCodes.getFirst();
        String displayName = Locale.of("", code).getDisplayCountry(Locale.ENGLISH);
        return displayName.isBlank() ? code : displayName;
    }

    record BrightDataCompanyResult(List<BrightDataCompany> hits) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record BrightDataCompany(String name, String about, String industries, String headquarters,
                             List<String> countryCodesArray, Integer employeesInLinkedin,
                             String website, Integer founded, String logo, String url) {}
}
