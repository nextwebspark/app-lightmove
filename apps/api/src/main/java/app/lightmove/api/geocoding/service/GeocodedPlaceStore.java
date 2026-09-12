package app.lightmove.api.geocoding.service;

import app.lightmove.api.geocoding.constant.GeoPrecision;
import app.lightmove.api.geocoding.model.GeoPoint;
import app.lightmove.api.geocoding.model.GeocodedPlace;
import app.lightmove.api.geocoding.model.PlaceKey;
import app.lightmove.api.geocoding.repository.GeocodedPlaceRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The cache's two transactions, on a bean of their own.
 *
 * <p>Its own bean for the reason {@code AuditEventWriter} is: {@code GeocodingService} calls the
 * vendor between the read and the write, and a vendor call must not sit inside a transaction — a
 * permit wait plus retry backoff would hold a database connection for seconds. Split across a bean
 * boundary, each half is a real transaction and the call in between is in none.
 *
 * <p>The write is an upsert rather than {@code save}: two map reads racing on the same unresolved
 * city both resolve it, and the second must land on the row the first made rather than on the unique
 * index.
 */
@Component
@RequiredArgsConstructor
public class GeocodedPlaceStore {

    private static final String UPSERT = """
            INSERT INTO app_lm_geocoded_place
                (id, place_key, city, country, latitude, longitude, precision, resolved_at)
            VALUES (:id, :placeKey, :city, :country, :latitude, :longitude, :precision, now())
            ON CONFLICT (place_key) DO UPDATE SET
                latitude    = EXCLUDED.latitude,
                longitude   = EXCLUDED.longitude,
                precision   = EXCLUDED.precision,
                resolved_at = EXCLUDED.resolved_at
            """;

    private final GeocodedPlaceRepository places;
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Map<String, GeocodedPlace> find(Collection<String> placeKeys) {
        if (placeKeys.isEmpty()) {
            return Map.of();
        }
        return places.findByPlaceKeyIn(placeKeys).stream()
                .collect(Collectors.toMap(GeocodedPlace::getPlaceKey, Function.identity()));
    }

    /** Remembers an answer — including no answer, so an unplaceable city is not asked again. */
    @Transactional
    public void remember(PlaceKey place, Optional<GeoPoint> point) {
        GeoPrecision precision = point.map(GeoPoint::precision)
                .orElse(place.hasCity() ? GeoPrecision.CITY : GeoPrecision.COUNTRY);
        jdbc.update(UPSERT, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("placeKey", place.key())
                .addValue("city", place.city())
                .addValue("country", place.country())
                .addValue("latitude", point.map(GeoPoint::latitude).orElse(null))
                .addValue("longitude", point.map(GeoPoint::longitude).orElse(null))
                .addValue("precision", precision.name()));
    }
}
