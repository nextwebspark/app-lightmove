package app.lightmove.api.core.resilience.model;

import java.time.Duration;

/**
 * Everything {@code VendorClientFactory} needs to build one vendor's client.
 *
 * <p>The auth header is a name and a value rather than a scheme, because vendors disagree: one wants
 * {@code Authorization: Bearer …}, the next a bare {@code X-API-Key}. Nothing branches on which
 * vendor this is — an integration is a config block, the same way an identity provider is.
 */
public record VendorClientSpec(String vendor, String baseUrl, String authHeader, String authHeaderValue,
                               Duration readTimeout, int requestsPerSecond) {

    public static VendorClientSpec bearer(String vendor, String baseUrl, String apiKey,
                                          Duration readTimeout, int requestsPerSecond) {
        return new VendorClientSpec(vendor, baseUrl, "Authorization", "Bearer " + apiKey,
                readTimeout, requestsPerSecond);
    }
}
