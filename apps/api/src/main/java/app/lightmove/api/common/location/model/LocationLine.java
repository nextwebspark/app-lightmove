package app.lightmove.api.common.location.model;

import app.lightmove.api.common.location.service.Countries;
import java.util.Arrays;

/**
 * A place written as one line — "Dubai, United Arab Emirates", "Greater Dubai Area, UAE" — read as the
 * city and country it names. Vendors publish a location this way and the schema stores the halves
 * apart; three enrichers were each doing their own {@code split(",")} before this existed, and one of
 * them wrote a bare country code into the country column.
 *
 * <p>Only the tail is tested against the country catalog: a line whose last segment names no country
 * is all city.
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
            // No country on the end: the whole line is the city. Only the first segment, unlike the
            // resolved-tail branch below — an unresolved tail is a region or a state, and the city
            // column is read as one city by the geocoding cache key and the alias catalog.
            return Countries.resolveSpelling(cleaned)
                    .map(only -> new LocationLine(null, only.name()))
                    .orElseGet(() -> new LocationLine(Countries.cityOf(firstOf(parts)), null));
        }
        // Everything before the country, not just the first segment: a brief may name two cities, and
        // "Sandton, Johannesburg, South Africa" is not in Sandton alone.
        String city = String.join(", ", Arrays.copyOfRange(parts, 0, parts.length - 1))
                .replaceAll("\\s+", " ").trim();
        return new LocationLine(city.isEmpty() ? null : Countries.cityOf(city), country);
    }

    /**
     * The country half, or {@code fallback} where the line named none — how a null column is filled.
     * A caller's fallback is usually a vendor's own ISO code, which is better evidence than prose.
     */
    public String countryOr(String fallback) {
        String fromFallback = Countries.nameOf(fallback);
        return fromFallback != null ? fromFallback : country;
    }

    private static String firstOf(String[] parts) {
        return parts.length == 0 ? null : parts[0].trim();
    }
}
