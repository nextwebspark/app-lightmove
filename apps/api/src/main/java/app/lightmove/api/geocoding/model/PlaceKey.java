package app.lightmove.api.geocoding.model;

import java.util.Locale;
import java.util.Optional;

/**
 * A normalised (trimmed, collapsed, lower-cased) city and country as a cache key. Either may be
 * absent, never both.
 */
public record PlaceKey(String city, String country) {

    public static Optional<PlaceKey> of(String city, String country) {
        String normalisedCity = normalise(city);
        String normalisedCountry = normalise(country);
        if (normalisedCity == null && normalisedCountry == null) {
            return Optional.empty();
        }
        return Optional.of(new PlaceKey(normalisedCity, normalisedCountry));
    }

    /** {@code "dubai|united arab emirates"}, empty halves included. */
    public String key() {
        return (city == null ? "" : city) + "|" + (country == null ? "" : country);
    }

    public boolean hasCity() {
        return city != null;
    }

    public boolean hasCountry() {
        return country != null;
    }

    static String normalise(String value) {
        if (value == null) {
            return null;
        }
        String collapsed = value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return collapsed.isEmpty() ? null : collapsed;
    }
}
