package app.lightmove.api.common.location.model;

import app.lightmove.api.common.location.service.Countries;
import java.util.Arrays;

/**
 * A one-line place ("Dubai, United Arab Emirates") read as its city and country. Only the tail is
 * tested against the country catalog; a line whose last segment names no country is all city.
 */
public record LocationLine(String city, String country) {

    public static LocationLine of(String line) {
        // Stray separators are stripped before the split, not after: ",".split(",") is an *empty*
        // array, so reading its last element threw, and "Dubai," kept its comma as part of the city.
        String cleaned = line == null ? "" : line.replaceAll("^[,\\s]+|[,\\s]+$", "");
        if (cleaned.isEmpty()) {
            return new LocationLine(null, null);
        }
        String[] parts = cleaned.split(",");
        String tail = parts[parts.length - 1].trim();
        // Spelled out only: "IL", "CA" and "MA" are ISO country codes as well as US states, and a
        // line ending in one is a city and a state far more often than it is a country.
        String country = parts.length > 1 ? Countries.resolveSpelling(tail).map(Country::name).orElse(null) : null;
        if (country == null) {
            // Only the first segment: an unresolved tail is a region or state, and the geocoding cache
            // key reads the city column as one city.
            return Countries.resolveSpelling(cleaned)
                    .map(only -> new LocationLine(null, only.name()))
                    .orElseGet(() -> new LocationLine(Countries.cityOf(firstOf(parts)), null));
        }
        // Everything before the country: "Sandton, Johannesburg, South Africa" is not in Sandton alone.
        String city = String.join(", ", Arrays.copyOfRange(parts, 0, parts.length - 1))
                .replaceAll("\\s+", " ").trim();
        return new LocationLine(city.isEmpty() ? null : Countries.cityOf(city), country);
    }

    /** The fallback's country where it names one — usually a vendor's ISO code, better than prose. */
    public String countryOr(String fallback) {
        String fromFallback = Countries.nameOf(fallback);
        return fromFallback != null ? fromFallback : country;
    }

    private static String firstOf(String[] parts) {
        return parts.length == 0 ? null : parts[0].trim();
    }
}
