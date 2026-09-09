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
 * Points for a set of places: from the cache where it can, from the vendor where it must, and never
 * more of the latter than one read is allowed to spend.
 *
 * <p>Deliberately not {@code @Transactional}. The read and the write each open their own transaction
 * in {@link GeocodedPlaceStore}; the vendor call sits between them in none.
 *
 * <p>A vendor that cannot answer costs the read that place and the ones after it, not the read: the
 * map still renders with what the cache already held, the caller reports the rest as pending, and
 * the next read tries again. One failure stops the loop because the failures that reach here —
 * credentials, quota, an outage — are the same for every place in the loop, and fifty attempts at a
 * vendor that is down is fifty backoffs the request timeout cannot afford.
 */
@Service
@Slf4j
public class GeocodingService {

    private final Geocoder geocoder;
    private final GeocodedPlaceStore store;
    private final MapboxSettings mapbox;
    private final TalentMapSettings budget;

    // Hand-written rather than @RequiredArgsConstructor: it derives two settings branches from the
    // properties root rather than taking them, which is the one case the Lombok rule exempts.
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

        int asked = 0;
        for (PlaceKey place : unresolved) {
            if (asked >= budget.geocodesPerRead()) {
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

    /** The city if it can be placed, else the country it is in, else nothing. */
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
