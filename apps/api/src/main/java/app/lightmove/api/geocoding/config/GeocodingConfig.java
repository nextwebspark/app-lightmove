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
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.client.RestClient;

/** Picks the {@link Geocoder}; bean shape as {@code CandidateEnrichmentConfig}, for its reasons. */
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
    Geocoder geocoder(@Autowired(required = false) @Qualifier("mapboxGeocoder") MapboxGeocoder mapbox,
                      LightMoveProperties properties, Environment environment) {
        if (mapbox == null) {
            log.info("Geocoding is off — no Mapbox token configured, so the map view is not offered.");
            return new LogGeocoder();
        }
        warnIfGeocodingWithTheBrowsersToken(properties.mapbox(), environment);
        log.info("Geocoding resolves places through Mapbox; answers are cached in app_lm_geocoded_place");
        return mapbox;
    }

    /**
     * Falling back to the browser's public token lets any signed-in user lift the geocoding quota from
     * the network tab, so a deployment doing it is warned in the logs rather than by the bill.
     */
    private void warnIfGeocodingWithTheBrowsersToken(MapboxSettings config, Environment environment) {
        boolean isDeveloperProfile = environment.acceptsProfiles(Profiles.of("local", "test", "e2e"));
        if (isDeveloperProfile || (config.geocodingToken() != null && !config.geocodingToken().isBlank())) {
            return;
        }
        log.warn("Mapbox geocoding is using the browser's public token — set lightmove.mapbox.geocoding-token "
                + "to a server-only token, and URL-restrict the public one.");
    }
}
