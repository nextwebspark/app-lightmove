package app.lightmove.api.core.resilience.model;

import java.time.Duration;

/**
 * What {@code VendorClientFactory} needs for one vendor: an auth header name and value, or a
 * {@link #queryToken} parameter (Mapbox), appended as the request leaves so no failure message
 * quotes the key. Nothing branches on which vendor it is.
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
