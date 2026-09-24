package app.lightmove.api.enrichment.company.service;

import app.lightmove.api.common.location.model.LocationLine;
import app.lightmove.api.core.config.BrightDataSettings;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.core.resilience.service.VendorRetryPredicate;
import app.lightmove.api.enrichment.common.service.BrightDataSearch;
import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Answers from the record Bright Data's LinkedIn company dataset already holds — the same indexed
 * lookup the person enrichment uses, against the companies dataset, whose slug field is {@code id}
 * (verified live; {@code linkedin_id} errors and {@code company_id} is LinkedIn's numeric id). It
 * shares the people lookup's timeout and retry budget because it shares the endpoint that got slow.
 *
 * <p>The response is read as a tree and converted, rather than bound straight to the record: the hit
 * is kept verbatim so {@code app_lm_vendor_company.raw} can be re-mapped when the industry map improves,
 * instead of the company being bought a second time.
 */
@Slf4j
public class BrightDataCompanyEnricher implements LinkedInCompanyEnricher {

    private static final String VENDOR = "brightdata";
    private static final int NAME_SEARCH_HITS = 10;

    private final RestClient client;
    private final String datasetId;
    private final VendorCallGuard guard;
    private final ObjectMapper json;

    public BrightDataCompanyEnricher(BrightDataSettings config, VendorClientFactory clientFactory,
                                     VendorRateLimiter rateLimiter, VendorCallGuard guard,
                                     RestClient.Builder builder, ObjectMapper json) {
        this.guard = guard;
        this.json = json;
        this.datasetId = config.companyDatasetId();
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
    public Optional<VendorCompanyRecord> fetch(String linkedinSlug) {
        JsonNode result = guard.call(VendorCall.of(VENDOR, "company-search"),
                () -> client.post()
                        .uri("/datasets/search/{datasetId}", datasetId)
                        .body(BrightDataSearch.exactlyOneWhere("id", linkedinSlug))
                        .retrieve()
                        .body(JsonNode.class));

        JsonNode hits = result == null ? null : result.get("hits");
        if (hits == null || hits.isEmpty()) {
            log.info("Bright Data company dataset holds no record for {}", linkedinSlug);
            return Optional.empty();
        }
        return toRecord(linkedinSlug, hits.get(0), json);
    }

    /**
     * {@code includes} is a case-insensitive substring match. The country is matched in the codes
     * array rather than {@code country_code}, which holds "AE,GB,KW" for a company in several; and
     * the caller's headcount floor keeps a one-person namesake from being billed as a hit.
     */
    @Override
    @Retryable(
            predicate = VendorRetryPredicate.class,
            maxRetriesString = "${lightmove.enrichment.brightdata.max-retries}",
            delayString = "${lightmove.resilience.retry-delay}",
            jitterString = "${lightmove.resilience.retry-jitter}",
            multiplierString = "${lightmove.resilience.retry-multiplier}",
            maxDelayString = "${lightmove.resilience.retry-max-delay}")
    public List<VendorCompanyRecord> searchByName(String namePart, String countryCode, int minEmployees) {
        JsonNode result = guard.call(VendorCall.of(VENDOR, "company-name-search"),
                () -> client.post()
                        .uri("/datasets/search/{datasetId}", datasetId)
                        .body(namedIn(namePart, countryCode, minEmployees))
                        .retrieve()
                        .body(JsonNode.class));
        JsonNode hits = result == null ? null : result.get("hits");
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        return hits.valueStream()
                .filter(hit -> hit.hasNonNull("id"))
                .flatMap(hit -> toRecord(hit.get("id").asString().toLowerCase(Locale.ROOT), hit, json).stream())
                .toList();
    }

    static Map<String, Object> namedIn(String namePart, String countryCode, int minEmployees) {
        Map<String, Object> named = Map.of("name", "name", "operator", "includes", "value", namePart);
        Map<String, Object> bigEnough = Map.of("name", "employees_in_linkedin", "operator", ">=",
                "value", minEmployees);
        List<Map<String, Object>> filters = countryCode == null ? List.of(named, bigEnough)
                : List.of(named, Map.of("name", "country_codes_array", "operator", "array_includes",
                        "value", countryCode), bigEnough);
        return Map.of("size", NAME_SEARCH_HITS, "filter", Map.of("operator", "and", "filters", filters));
    }

    static Optional<VendorCompanyRecord> toRecord(String linkedinSlug, JsonNode hit, ObjectMapper json) {
        BrightDataCompany company = json.treeToValue(hit, BrightDataCompany.class);
        if (company.name() == null || company.name().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new VendorCompanyRecord(
                linkedinSlug,
                company.name(),
                company.industries(),
                countryOf(company.countryCodesArray(), company.headquarters()),
                LocationLine.of(company.headquarters()).city(),
                company.employeesInLinkedin(),
                company.website(),
                company.url(),
                company.founded(),
                company.about(),
                company.logo(),
                keywordsOf(company.specialties()),
                hit.toString()));
    }

    /**
     * The dataset speaks ISO-2 codes and the Country column speaks names, as the Apollo rows do. The
     * headquarters line answers where the codes array is empty — it usually ends in the country.
     */
    private static String countryOf(List<String> countryCodes, String headquarters) {
        String code = countryCodes == null || countryCodes.isEmpty() ? null : countryCodes.getFirst();
        return LocationLine.of(headquarters).countryOr(code);
    }

    /**
     * One comma-separated line on the page — "insurance software, insurance platform" — becomes the
     * lower-cased keywords the market-segment filter matches, which is how
     * {@code app_lm_apollo_companies.keywords} already spells them.
     */
    private static List<String> keywordsOf(String specialties) {
        if (specialties == null || specialties.isBlank()) {
            return List.of();
        }
        return Arrays.stream(specialties.split(","))
                .map(specialty -> specialty.trim().toLowerCase(Locale.ROOT))
                .filter(specialty -> !specialty.isEmpty())
                .distinct()
                .toList();
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record BrightDataCompany(String name, String about, String industries, String headquarters,
                             List<String> countryCodesArray, Integer employeesInLinkedin,
                             String website, Integer founded, String logo, String url,
                             String specialties) {}
}
