package app.lightmove.api.geocoding.config;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.MapboxSettings;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.geocoding.service.Geocoder;
import app.lightmove.api.geocoding.service.LogGeocoder;
import app.lightmove.api.geocoding.service.MapboxGeocoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Picks the {@link Geocoder} from config. The Mapbox adapter is its own {@code @Bean}, and
 * {@code defaultCandidate = false}, for the two reasons {@code CandidateEnrichmentConfig} explains: an
 * adapter constructed inline inside another factory method is never proxied and its
 * {@code @Retryable} would be inert, and an adapter left as an ordinary candidate would make injecting
 * the seam ambiguous.
 */
@Configuration
@Slf4j
public class GeocodingConfig {

    @Bean(defaultCandidate = false)
    MapboxGeocoder mapboxGeocoder(LightMoveProperties properties, VendorClientFactory clientFactory,
                                  VendorRateLimiter rateLimiter, VendorCallGuard guard) {
        MapboxSettings config = properties.mapbox();
        if (!config.isConfigured()) {
            return null;
        }
        return new MapboxGeocoder(config, clientFactory, rateLimiter, guard, RestClient.builder());
    }

    @Bean
    Geocoder geocoder(@Autowired(required = false) @Qualifier("mapboxGeocoder") MapboxGeocoder mapbox) {
        if (mapbox == null) {
            log.info("Geocoding is off — no Mapbox token configured, so the map view is not offered.");
            return new LogGeocoder();
        }
        log.info("Geocoding resolves places through Mapbox; answers are cached in app_lm_geocoded_place");
        return mapbox;
    }
}
