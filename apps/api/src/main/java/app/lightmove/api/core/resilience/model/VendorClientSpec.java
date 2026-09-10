package app.lightmove.api.core.resilience.model;

import java.time.Duration;

/**
 * Everything {@code VendorClientFactory} needs to build one vendor's client.
 *
 * <p>The auth header is a name and a value rather than a scheme, because vendors disagree: one wants
 * {@code Authorization: Bearer …}, the next a bare {@code X-API-Key}. Nothing branches on which
 * vendor this is — an integration is a config block, the same way an identity provider is.
 *
 * <p>A vendor that authenticates by <b>query parameter</b> — Mapbox — is the third shape: the header
 * pair is null and {@link #queryToken} names the parameter instead. The factory appends it as the
 * request leaves, so no adapter builds a URI with the key in it — and no failure message quotes one.
 */
public record VendorClientSpec(String vendor, String baseUrl, String authHeader, String authHeaderValue,
                               String queryTokenName, String queryTokenValue,
                               Duration readTimeout, int requestsPerSecond) {

    public static VendorClientSpec bearer(String vendor, String baseUrl, String apiKey,
                                          Duration readTimeout, int requestsPerSecond) {
        return new VendorClientSpec(vendor, baseUrl, "Authorization", "Bearer " + apiKey, null, null,
                readTimeout, requestsPerSecond);
    }

    public static VendorClientSpec header(String vendor, String baseUrl, String headerName, String headerValue,
                                          Duration readTimeout, int requestsPerSecond) {
        return new VendorClientSpec(vendor, baseUrl, headerName, headerValue, null, null,
                readTimeout, requestsPerSecond);
    }

    public static VendorClientSpec queryToken(String vendor, String baseUrl, String parameterName,
                                              String token, Duration readTimeout, int requestsPerSecond) {
        return new VendorClientSpec(vendor, baseUrl, null, null, parameterName, token,
                readTimeout, requestsPerSecond);
    }

    public boolean authenticatesByHeader() {
        return authHeader != null;
    }

    public boolean authenticatesByQuery() {
        return queryTokenName != null;
    }
}
