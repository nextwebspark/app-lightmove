package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The Mapbox account behind the talent map — {@code lightmove.mapbox.*}. The <b>public</b> token is
 * URL-restricted for the browser, which fails server-side (no Referer), hence a separate
 * <b>geocoding</b> token; blank falls back to the public one. A blank public token hides the map.
 */
public record MapboxSettings(
        String publicToken,
        String geocodingToken,
        @DefaultValue("https://api.mapbox.com") String baseUrl,

        /** Mapbox caps temporary geocoding at about a thousand requests a minute; paced well under. */
        @DefaultValue("10") int requestsPerSecond,

        /**
         * Whether the account holds Mapbox's <i>Permanent Geocoding</i> entitlement. Storing a
         * temporary result indefinitely is against their terms, so without it a cached place is
         * re-asked after {@link #cacheTtl}; with it the request says {@code permanent=true} and the row
         * never expires.
         */
        @DefaultValue("false") boolean permanentGeocoding,
        @DefaultValue("30d") Duration cacheTtl
) {

    public boolean isConfigured() {
        return publicToken != null && !publicToken.isBlank();
    }

    /** The token the server geocodes with: its own, or the browser's when none was set apart. */
    public String effectiveGeocodingToken() {
        if (geocodingToken != null && !geocodingToken.isBlank()) {
            return geocodingToken;
        }
        return publicToken;
    }
}
