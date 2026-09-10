package app.lightmove.api.geocoding.repository;

import app.lightmove.api.geocoding.model.GeocodedPlace;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** The cache's one read. Writes go through {@code GeocodedPlaceStore}'s upsert, never {@code save}. */
public interface GeocodedPlaceRepository extends JpaRepository<GeocodedPlace, UUID> {

    List<GeocodedPlace> findByPlaceKeyIn(Collection<String> placeKeys);
}
