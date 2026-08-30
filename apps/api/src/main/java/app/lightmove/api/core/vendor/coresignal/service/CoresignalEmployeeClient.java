package app.lightmove.api.core.vendor.coresignal.service;

import app.lightmove.api.core.config.CoresignalSettings;
import app.lightmove.api.core.config.VendorSettings;
import app.lightmove.api.core.vendor.coresignal.model.CoresignalEmployeeReference;
import app.lightmove.api.core.vendor.model.VendorCall;
import app.lightmove.api.core.vendor.model.VendorClientSpec;
import app.lightmove.api.core.vendor.service.VendorCallGuard;
import app.lightmove.api.core.vendor.service.VendorClientFactory;
import app.lightmove.api.core.vendor.service.VendorRetryPredicate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.client.RestClient;

/**
 * Asks Coresignal who works at a company we already hold.
 *
 * <p><b>This is a worked example of the vendor layer, not the sourcing pipeline.</b> It exists so the
 * layer ships with a real caller rather than as an abstraction nobody uses, and it is deliberately
 * two lookups wide. The seniority recall filter, pagination, the collect endpoints that turn an
 * identifier into a profile, deduplication and persistence all belong to the session that builds
 * sourcing properly. The request paths and filter field names below are from Coresignal's published
 * documentation and must be re-checked against the live API before this is switched on.
 *
 * <p>Note the direction: Coresignal is a source of <b>people</b>. The company universe is ours, and
 * we hand it a company rather than asking it which companies exist.
 *
 * <p>Every method here is the template for every future vendor adapter:
 * <ol>
 *   <li>{@code @Retryable} naming {@link VendorRetryPredicate}, so what is worth retrying is decided
 *       once for the whole codebase rather than per method;</li>
 *   <li>the body wrapped in {@link VendorCallGuard}, which takes the rate-limit permit — inside the
 *       retry, so each attempt pays for its own — and classifies the failures no status handler can
 *       see;</li>
 *   <li>an {@code Optional} return, because "nobody matched" is an answer and must not be an
 *       exception;</li>
 *   <li>a record of ours coming back out, never a parsed Coresignal payload.</li>
 * </ol>
 *
 * <p><b>The cascade over these methods lives in {@code CoresignalEmployeeSearch}, and must.</b>
 * {@code @Retryable} is proxy-based, so a method on this class calling these ones through
 * {@code this} would bypass the proxy and silently lose every retry — the same trap that made
 * {@code @Async} inert on {@code AuditService}.
 */
public class CoresignalEmployeeClient {

    private static final String VENDOR = "coresignal";
    private static final String SEARCH_PATH = "/v2/employee_multi_source/search/filter";

    private final RestClient client;
    private final VendorCallGuard guard;

    public CoresignalEmployeeClient(CoresignalSettings config, VendorSettings vendorSettings,
                                    VendorClientFactory clientFactory, VendorCallGuard guard,
                                    RestClient.Builder builder) {
        this.guard = guard;
        this.client = clientFactory.create(new VendorClientSpec(
                VENDOR,
                config.baseUrl(),
                // Their own header name, not an Authorization bearer. An identity provider is a
                // config block and so is a vendor: nothing branches on which one this is.
                "apikey",
                config.apiKey(),
                "",
                null,
                vendorSettings.readTimeout(),
                config.requestsPerSecond(),
                // Their error bodies echo the filter, which carries a company and can carry a person.
                false), builder);
    }

    /**
     * The precise way to ask. In the Gulf a conglomerate runs many operating entities under one
     * corporate domain, so a company's own LinkedIn URL names the entity people actually work for
     * where its domain would return the whole group.
     */
    @Retryable(
            predicate = VendorRetryPredicate.class,
            maxRetriesString = "${lightmove.vendor.max-retries}",
            delayString = "${lightmove.vendor.retry-delay}",
            jitterString = "${lightmove.vendor.retry-jitter}",
            multiplierString = "${lightmove.vendor.retry-multiplier}",
            maxDelayString = "${lightmove.vendor.retry-max-delay}")
    public Optional<List<CoresignalEmployeeReference>> atCompanyLinkedInUrl(String companyLinkedInUrl) {
        return search("employee-search-by-company-linkedin-url",
                "active_experience_company_linkedin_url", companyLinkedInUrl);
    }

    /** The looser way, for a company whose LinkedIn URL we do not hold. */
    @Retryable(
            predicate = VendorRetryPredicate.class,
            maxRetriesString = "${lightmove.vendor.max-retries}",
            delayString = "${lightmove.vendor.retry-delay}",
            jitterString = "${lightmove.vendor.retry-jitter}",
            multiplierString = "${lightmove.vendor.retry-multiplier}",
            maxDelayString = "${lightmove.vendor.retry-max-delay}")
    public Optional<List<CoresignalEmployeeReference>> atCompanyWebsite(String companyDomain) {
        return search("employee-search-by-company-website",
                "active_experience_company_website", companyDomain);
    }

    /**
     * A search spends credits but changes nothing, so it is a read: safe to repeat, which is what
     * makes a timeout worth retrying rather than a possible double charge.
     */
    private Optional<List<CoresignalEmployeeReference>> search(String operation, String filterField, String value) {
        // A company we hold no identifier for is a question with nothing in it. Answering "nobody"
        // here rather than asking the caller to check first is what keeps the guarantee whole: every
        // public method on this class either returns an answer or throws a classified
        // VendorException, and never a NullPointerException from a filter built out of a null.
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        VendorCall call = VendorCall.read(VENDOR, operation);

        return guard.call(call, () -> {
            long[] ids = client.post()
                    .uri(SEARCH_PATH)
                    .body(Map.of(filterField, value))
                    .retrieve()
                    .body(long[].class);

            // An empty result is "nobody here", which is an answer a cascade acts on — not a failure.
            if (ids == null || ids.length == 0) {
                return Optional.empty();
            }
            return Optional.of(Arrays.stream(ids)
                    .mapToObj(CoresignalEmployeeReference::new)
                    .toList());
        });
    }
}
