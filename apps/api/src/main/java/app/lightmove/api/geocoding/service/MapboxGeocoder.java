package app.lightmove.api.geocoding.service;

import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.config.MapboxSettings;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.core.resilience.service.VendorRetryPredicate;
import app.lightmove.api.geocoding.constant.GeoPrecision;
import app.lightmove.api.geocoding.model.GeoPoint;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Mapbox Geocoding v6, forward. A city is asked as {@code place, locality, district} and pinned to its
 * country's code, and the hit is checked against that code again ("Salalah" is not in Yemen). The
 * point is read from {@code properties.coordinates}, not the longitude-first GeoJSON geometry. The
 * token is added by the vendor client factory, so no URI built here carries it.
 */
@Slf4j
public class MapboxGeocoder implements Geocoder {

    public static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private static final String VENDOR = "mapbox";
    private static final String CITY_TYPES = "place,locality,district";
    private static final String COUNTRY_TYPES = "country";

    private final RestClient client;
    private final VendorCallGuard guard;
    private final boolean permanent;

    public MapboxGeocoder(MapboxSettings config, VendorClientFactory clientFactory,
                          VendorRateLimiter rateLimiter, VendorCallGuard guard, RestClient.Builder builder) {
        this.guard = guard;
        this.permanent = config.permanentGeocoding();
        this.client = clientFactory.create(VendorClientSpec.queryToken(VENDOR, config.baseUrl(),
                "access_token", config.effectiveGeocodingToken(), READ_TIMEOUT,
                config.requestsPerSecond()), builder, rateLimiter);
    }

    @Override
    @Retryable(
            predicate = VendorRetryPredicate.class,
            maxRetriesString = "${lightmove.resilience.max-retries}",
            delayString = "${lightmove.resilience.retry-delay}",
            jitterString = "${lightmove.resilience.retry-jitter}",
            multiplierString = "${lightmove.resilience.retry-multiplier}",
            maxDelayString = "${lightmove.resilience.retry-max-delay}")
    public Optional<GeoPoint> city(String city, String country) {
        Optional<String> isoCode = Optional.ofNullable(Countries.codeOf(country));
        // A country we cannot code is still worth naming: "Salalah, Oman" beats "Salalah" alone.
        String query = isoCode.isPresent() || country == null ? city : city + ", " + country;
        MapboxFeatureCollection answer = guard.call(VendorCall.of(VENDOR, "forward-city"),
                () -> client.get()
                        .uri(uri -> forward(uri, query, CITY_TYPES, isoCode.orElse(null)))
                        .retrieve()
                        .body(MapboxFeatureCollection.class));
        return firstPoint(answer, isoCode.orElse(null), GeoPrecision.CITY);
    }

    @Override
    @Retryable(
            predicate = VendorRetryPredicate.class,
            maxRetriesString = "${lightmove.resilience.max-retries}",
            delayString = "${lightmove.resilience.retry-delay}",
            jitterString = "${lightmove.resilience.retry-jitter}",
            multiplierString = "${lightmove.resilience.retry-multiplier}",
            maxDelayString = "${lightmove.resilience.retry-max-delay}")
    public Optional<GeoPoint> country(String country) {
        Optional<String> isoCode = Optional.ofNullable(Countries.codeOf(country));
        MapboxFeatureCollection answer = guard.call(VendorCall.of(VENDOR, "forward-country"),
                () -> client.get()
                        .uri(uri -> forward(uri, country, COUNTRY_TYPES, isoCode.orElse(null)))
                        .retrieve()
                        .body(MapboxFeatureCollection.class));
        return firstPoint(answer, isoCode.orElse(null), GeoPrecision.COUNTRY);
    }

    private URI forward(UriBuilder uri, String query, String types, String isoCode) {
        return forwardUri(uri, query, types, isoCode, permanent);
    }

    static URI forwardUri(UriBuilder uri, String query, String types, String isoCode, boolean permanent) {
        uri.path("/search/geocode/v6/forward")
                .queryParam("q", query)
                .queryParam("types", types)
                .queryParam("limit", 1)
                .queryParam("language", "en");
        if (isoCode != null) {
            uri.queryParam("country", isoCode);
        }
        if (permanent) {
            uri.queryParam("permanent", true);
        }
        return uri.build();
    }

    static Optional<GeoPoint> firstPoint(MapboxFeatureCollection answer, String expectedIsoCode,
                                         GeoPrecision precision) {
        if (answer == null || answer.features() == null || answer.features().isEmpty()) {
            return Optional.empty();
        }
        MapboxFeatureProperties properties = answer.features().getFirst().properties();
        if (properties == null || properties.coordinates() == null) {
            return Optional.empty();
        }
        String answeredCode = properties.countryCode();
        if (expectedIsoCode != null && answeredCode != null && !answeredCode.equalsIgnoreCase(expectedIsoCode)) {
            log.info("Mapbox placed a {} query in {} rather than {}; ignoring it", precision,
                    answeredCode, expectedIsoCode);
            return Optional.empty();
        }
        MapboxCoordinates point = properties.coordinates();
        return Optional.of(new GeoPoint(point.latitude(), point.longitude(), precision));
    }

    record MapboxFeatureCollection(List<MapboxFeature> features) {}

    record MapboxFeature(MapboxFeatureProperties properties) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record MapboxFeatureProperties(String name, String featureType, MapboxCoordinates coordinates,
                                   MapboxContext context) {

        /** From the hit's context, or itself when the hit is a country. */
        String countryCode() {
            if (context != null && context.country() != null && context.country().countryCode() != null) {
                return context.country().countryCode();
            }
            return null;
        }
    }

    record MapboxCoordinates(double longitude, double latitude) {}

    record MapboxContext(MapboxCountry country) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record MapboxCountry(String name, String countryCode) {}
}
