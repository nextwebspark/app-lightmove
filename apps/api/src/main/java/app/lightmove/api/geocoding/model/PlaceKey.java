package app.lightmove.api.geocoding.model;

import java.util.Locale;
import java.util.Optional;

/**
 * A city and a country as a cache key: what two rows must share to be the same place.
 *
 * <p>Both halves are normalised — trimmed, inner whitespace collapsed, lower-cased — so "Dubai " and
 * "dubai" are one lookup. Either may be absent, never both: a company with a country and no city is a
 * place at country precision, and one with neither has nothing to resolve.
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

    /** The column the cache is keyed on: {@code "dubai|united arab emirates"}, empty halves included. */
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
