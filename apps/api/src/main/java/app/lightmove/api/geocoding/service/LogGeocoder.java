package app.lightmove.api.geocoding.service;

import app.lightmove.api.geocoding.model.GeoPoint;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/** What runs when no Mapbox token is configured: places nothing, so every row stays unlocated. */
@Slf4j
public class LogGeocoder implements Geocoder {

    @Override
    public Optional<GeoPoint> city(String city, String country) {
        log.debug("Geocoding is off; {} stays unlocated", city);
        return Optional.empty();
    }

    @Override
    public Optional<GeoPoint> country(String country) {
        log.debug("Geocoding is off; {} stays unlocated", country);
        return Optional.empty();
    }
}
