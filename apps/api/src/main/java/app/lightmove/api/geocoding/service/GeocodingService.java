package app.lightmove.api.geocoding.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.MapboxSettings;
import app.lightmove.api.core.config.TalentMapSettings;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.geocoding.model.GeoPoint;
import app.lightmove.api.geocoding.model.GeocodedPlace;
import app.lightmove.api.geocoding.model.GeocodingResult;
import app.lightmove.api.geocoding.model.PlaceKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Points for a set of places, from the cache or the vendor. Not {@code @Transactional}: the vendor
 * call sits between {@link GeocodedPlaceStore}'s own transactions. One vendor failure stops the loop
 * and the rest are reported pending; a deadline beside the count stops a merely slow vendor running
 * past the gateway's timeout, which would lose even the places resolved.
 */
@Service
@Slf4j
public class GeocodingService {

    private final Geocoder geocoder;
    private final GeocodedPlaceStore store;
    private final MapboxSettings mapbox;
    private final TalentMapSettings budget;

    public GeocodingService(Geocoder geocoder, GeocodedPlaceStore store, LightMoveProperties properties) {
        this.geocoder = geocoder;
        this.store = store;
        this.mapbox = properties.mapbox();
        this.budget = properties.talentMap();
    }

    public GeocodingResult resolve(Set<PlaceKey> places) {
        if (places.isEmpty()) {
            return new GeocodingResult(Map.of(), 0);
        }
        Map<String, GeocodedPlace> cached = store.find(places.stream().map(PlaceKey::key).toList());
        Instant staleBefore = mapbox.permanentGeocoding() ? Instant.MIN : Instant.now().minus(mapbox.cacheTtl());

        Map<PlaceKey, GeoPoint> points = new HashMap<>();
        List<PlaceKey> unresolved = new ArrayList<>();
        for (PlaceKey place : places) {
            GeocodedPlace held = cached.get(place.key());
            if (held == null || held.getResolvedAt().isBefore(staleBefore)) {
                unresolved.add(place);
                // A stale hit is still the best answer this read has while it is re-asked below.
                if (held != null) {
                    held.point().ifPresent(point -> points.put(place, point));
                }
                continue;
            }
            held.point().ifPresent(point -> points.put(place, point));
        }

        // With geocoding off a stored miss would outlive the day someone configures a token.
        if (!geocoder.isEnabled()) {
            return new GeocodingResult(Map.copyOf(points), 0);
        }

        int asked = 0;
        Instant deadline = Instant.now().plus(budget.geocodingDeadline());
        for (PlaceKey place : unresolved) {
            if (asked >= budget.geocodesPerRead() || Instant.now().isAfter(deadline)) {
                break;
            }
            asked++;
            Optional<GeoPoint> point;
            try {
                point = lookup(place);
            } catch (VendorException failure) {
                log.warn("Geocoding stopped for this read after {}: {}", place.key(), failure.getKind());
                asked--;
                break;
            }
            store.remember(place, point);
            if (point.isPresent()) {
                points.put(place, point.get());
            } else {
                points.remove(place);
            }
        }
        return new GeocodingResult(Map.copyOf(points), unresolved.size() - asked);
    }

    /** The city if it can be placed, else its country. */
    private Optional<GeoPoint> lookup(PlaceKey place) {
        if (place.hasCity()) {
            Optional<GeoPoint> city = geocoder.city(place.city(), place.country());
            if (city.isPresent()) {
                return city;
            }
        }
        if (place.hasCountry()) {
            return geocoder.country(place.country());
        }
        return Optional.empty();
    }
}
