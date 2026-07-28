package app.lightmove.api.company.service;

import app.lightmove.api.company.model.CoreSignalCompanyRecord;
import app.lightmove.api.company.model.CoreSignalSearchCriteria;
import app.lightmove.api.company.model.CoreSignalSearchResult;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The real CoreSignal adapter — two endpoints of their Multi-source Company API over Spring's
 * {@code RestClient}. Constructed by {@code CoreSignalConfig} (not component-scanned), the same
 * arrangement as {@code ResendEmailSender}: the config decides whether this class or the
 * unconfigured stand-in is live.
 *
 * <p>Collect responses are parsed defensively — CoreSignal documents 500+ fields but a live record
 * populates an unpredictable subset, and a couple of field names (LinkedIn URL, logo) could not be
 * verified from their docs at all. Extraction misses degrade to {@code null} columns; the verbatim
 * payload is preserved so a corrected extraction can be re-run from the database for free.
 */
@Slf4j
public class CoreSignalClient implements CoreSignalGateway {

    /** Search is an Elasticsearch query on their side — generous, but bounded: no hung run threads. */
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private static final String SEARCH_PATH = "/company_multi_source/search/es_dsl";
    private static final String COLLECT_PATH = "/company_multi_source/collect/{id}";
    private static final String TOTAL_RESULTS_HEADER = "x-total-results";

    private final RestClient client;
    private final CoreSignalQueryBuilder queryBuilder;
    private final ObjectMapper json;

    public CoreSignalClient(String apiKey, String baseUrl, RestClient.Builder builder,
                            CoreSignalQueryBuilder queryBuilder, ObjectMapper json) {
        this.queryBuilder = queryBuilder;
        this.json = json;
        this.client = builder
                .baseUrl(baseUrl)
                .defaultHeader("apikey", apiKey)
                .build();
    }

    @Override
    public CoreSignalSearchResult searchCompanyIds(CoreSignalSearchCriteria criteria, int limit) {
        try {
            var response = client.post()
                    .uri(SEARCH_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(queryBuilder.searchBody(criteria))
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<List<Long>>() {});

            List<Long> ids = response.getBody() == null ? List.of() : response.getBody();
            List<Long> kept = ids.size() > limit ? ids.subList(0, limit) : ids;
            long totalMatched = parseTotal(response.getHeaders().getFirst(TOTAL_RESULTS_HEADER), ids.size());
            log.info("CoreSignal search matched {} companies, kept {}", totalMatched, kept.size());
            return new CoreSignalSearchResult(List.copyOf(kept), totalMatched);
        } catch (RestClientResponseException ex) {
            // A failed search dooms the whole run whatever the status — translate uniformly as fatal.
            throw new CoreSignalUnavailableException(failureDetail("search", ex.getStatusCode()), true, ex);
        } catch (RuntimeException ex) {
            throw new CoreSignalUnavailableException("CoreSignal search failed: " + ex.getMessage(), true, ex);
        }
    }

    @Override
    public Optional<CoreSignalCompanyRecord> collect(long coresignalId) {
        String body;
        try {
            body = client.get()
                    .uri(COLLECT_PATH, coresignalId)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                // The id came from a search moments ago but the record is gone — their data churn,
                // not an error worth failing a run over.
                log.warn("CoreSignal no longer knows company {}", coresignalId);
                return Optional.empty();
            }
            boolean fatal = ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 402;
            throw new CoreSignalUnavailableException(
                    failureDetail("collect of " + coresignalId, ex.getStatusCode()), fatal, ex);
        } catch (RuntimeException ex) {
            throw new CoreSignalUnavailableException(
                    "CoreSignal collect of %d failed: %s".formatted(coresignalId, ex.getMessage()), false, ex);
        }
        return Optional.of(parseCollectResponse(coresignalId, body));
    }

    private CoreSignalCompanyRecord parseCollectResponse(long coresignalId, String body) {
        JsonNode node = json.readTree(body == null ? "{}" : body);
        return new CoreSignalCompanyRecord(
                coresignalId,
                text(node, "company_name") != null ? text(node, "company_name") : "(unnamed company)",
                text(node, "website"),
                linkedinUrl(node),
                text(node, "description"),
                text(node, "industry"),
                intOrNull(node, "employees_count"),
                text(node, "size_range"),
                revenueAnnual(node),
                text(node, "revenue_annual_range"),
                text(node, "hq_location"),
                text(node, "hq_country"),
                text(node, "hq_country_iso2"),
                intOrNull(node, "founded_year"),
                logoUrl(node),
                node.toString());
    }

    /**
     * The LinkedIn-profile field name is the one thing CoreSignal's docs would not confirm. Try the
     * documented-adjacent candidates in order, then fall back to scanning the record's top-level
     * strings for a LinkedIn company URL.
     */
    private static String linkedinUrl(JsonNode node) {
        String direct = firstText(node, "professional_network_url", "linkedin_url");
        if (direct != null) {
            return direct;
        }
        for (JsonNode value : node) {
            if (value.isString() && value.stringValue().contains("linkedin.com/company/")) {
                return value.stringValue();
            }
        }
        return null;
    }

    /** Prefer the URL form; the base64 {@code company_logo} becomes a data URI the SPA can render. */
    private static String logoUrl(JsonNode node) {
        String url = firstText(node, "company_logo_url", "logo_url");
        if (url != null && url.startsWith("http")) {
            return url;
        }
        String raw = firstText(node, "company_logo");
        if (raw == null) {
            return null;
        }
        return raw.startsWith("http") ? raw : "data:image/png;base64," + raw;
    }

    /**
     * Revenue lives in a nested multi-source structure; try the documented sortable path first,
     * then the flat field. Units assumed USD — see {@code CoreSignalQueryBuilder#REVENUE_FIELD}.
     */
    private static BigDecimal revenueAnnual(JsonNode node) {
        JsonNode nested = node.path("revenue_annual").path("source_1_annual_revenue").path("annual_revenue");
        if (nested.isNumber()) {
            return nested.decimalValue();
        }
        JsonNode flat = node.path("revenue_annual");
        return flat.isNumber() ? flat.decimalValue() : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.stringValue().isBlank() ? value.stringValue() : null;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.intValue() : null;
    }

    private static long parseTotal(String header, long fallback) {
        if (header == null) {
            return fallback;
        }
        try {
            return Long.parseLong(header);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String failureDetail(String call, HttpStatusCode status) {
        String reason = switch (status.value()) {
            case 401 -> "invalid API key";
            case 402 -> "out of credits";
            case 429 -> "rate limited";
            default -> "HTTP " + status.value();
        };
        return "CoreSignal %s failed: %s".formatted(call, reason);
    }
}
