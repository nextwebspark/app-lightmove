package app.lightmove.api.core.vendor.model;

import java.time.Duration;

/**
 * Everything one vendor's HTTP client needs, so {@code VendorClientFactory} can be told rather than
 * having to know.
 *
 * <p>Per-vendor rather than one shared block of settings, because the differences between vendors are
 * exactly the things that matter here: one paginated search endpoint may need thirty seconds where a
 * mail API needs five, and one vendor's published rate cap has nothing to do with another's. A single
 * global timeout would force a refactor the moment the second vendor arrives.
 *
 * <p>{@code apiKeyHeader} is named rather than assumed: Coresignal wants {@code apikey}, Resend wants
 * {@code Authorization: Bearer}. Neither is ever a query parameter — see the factory.
 *
 * @param vendor            the rate-limit key and log label
 * @param baseUrl           scheme and host, no query string
 * @param apiKeyHeader      the header the key travels in
 * @param apiKey            the key itself; the factory refuses to build a client without one
 * @param authValuePrefix   what precedes the key in that header, e.g. {@code "Bearer "}; may be empty
 * @param connectTimeout    null to take the shared default
 * @param readTimeout       null to take the shared default
 * @param requestsPerSecond the vendor's published cap; 0 to leave the calls unpaced
 * @param captureErrorBody  whether a failed response body may be kept for diagnosis. Off unless the
 *                          vendor's error bodies are known not to echo the query, because they are
 *                          personal data the moment they do
 */
public record VendorClientSpec(
        String vendor,
        String baseUrl,
        String apiKeyHeader,
        String apiKey,
        String authValuePrefix,
        Duration connectTimeout,
        Duration readTimeout,
        int requestsPerSecond,
        boolean captureErrorBody
) {

    public String authHeaderValue() {
        return authValuePrefix == null ? apiKey : authValuePrefix + apiKey;
    }
}
