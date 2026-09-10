package app.lightmove.api.geocoding.service;

import app.lightmove.api.geocoding.model.GeoPoint;
import java.util.Optional;

/**
 * The vendor seam: one question per precision, so the caller — not the adapter — decides to fall back
 * from a city nobody could place to the country it is in. Two methods rather than one taking a
 * {@code PlaceKey}, because each is one HTTP call and one retry policy; a method that made both calls
 * would retry both when only the second had failed.
 *
 * <p>Empty means the vendor answered and had nothing; a vendor that could not answer throws
 * {@link app.lightmove.api.core.resilience.model.VendorException} and the caller decides what a read
 * without that place is worth.
 */
public interface Geocoder {

    /** The city's point, within the named country when one is known. */
    Optional<GeoPoint> city(String city, String country);

    /** The country's own point — a centroid, and the fallback when the city could not be placed. */
    Optional<GeoPoint> country(String country);
}
