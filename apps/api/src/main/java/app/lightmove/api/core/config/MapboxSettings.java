package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The Mapbox account behind the talent map — {@code lightmove.mapbox.*}.
 *
 * <p>Two tokens, because the two halves run in two places. The <b>public</b> token goes to the browser
 * and should be URL-restricted to the SPA's origin; a URL-restricted token fails from a server, which
 * sends no Referer, so the <b>geocoding</b> token is the server's own. Left blank it falls back to the
 * public one, which is right for a laptop and wrong for production.
 *
 * <p>A blank public token means the map view is not offered at all, so a fresh clone runs with no
 * Mapbox account.
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
