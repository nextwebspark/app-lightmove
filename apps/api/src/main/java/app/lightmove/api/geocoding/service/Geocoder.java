package app.lightmove.api.geocoding.service;

import app.lightmove.api.geocoding.model.GeoPoint;
import java.util.Optional;

/**
 * The vendor seam, one method per precision so each is one call and one retry policy, and the caller
 * decides the city-to-country fallback. Empty means the vendor had nothing; a vendor that could not
 * answer throws {@code VendorException}.
 */
public interface Geocoder {

    /** Within the named country when one is known. */
    Optional<GeoPoint> city(String city, String country);

    /** A centroid — the fallback when the city could not be placed. */
    Optional<GeoPoint> country(String country);

    /**
     * False for the no-vendor stand-in, whose silence is not an answer — storing it as a miss would keep
     * every place unlocated for the cache's lifetime after a token is set.
     */
    default boolean isEnabled() {
        return true;
    }
}
