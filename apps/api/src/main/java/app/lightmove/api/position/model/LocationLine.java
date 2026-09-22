package app.lightmove.api.position.model;

import app.lightmove.api.common.location.model.Country;
import app.lightmove.api.common.location.service.Countries;
import java.util.Optional;

/**
 * One line of prose naming where a role sits, read into the two halves the brief stores
 * ({@code location_city} and {@code location_country}, V66). A document writes "Dubai, United Arab
 * Emirates" on one line; the screen has a free-text city beside a country picker.
 *
 * <p>The catalog decides, never the comma. A tail that names no country the catalog knows keeps the
 * whole line as the city — "Chicago, IL" is one place a person will finish, not a city in Israel —
 * which is why this asks {@link Countries#resolveSpelling} rather than {@link Countries#resolve}:
 * the former refuses a bare alpha-2 code for exactly that reason.
 *
 * @param city    the free-text half, or null where the line names a country alone
 * @param country the catalog's own spelling of the country, or null where none was recognised
 */
public record LocationLine(String city, String country) {

    private static final LocationLine NOTHING = new LocationLine(null, null);

    public static LocationLine of(String line) {
        if (line == null || line.isBlank() || !namesSomewhere(line)) {
            return NOTHING;
        }
        String trimmed = line.trim();

        int lastComma = trimmed.lastIndexOf(',');
        if (lastComma >= 0) {
            String tail = trimmed.substring(lastComma + 1).trim();
            Optional<Country> country = Countries.resolveSpelling(tail);
            if (country.isPresent()) {
                String head = trimmed.substring(0, lastComma).trim();
                return new LocationLine(Countries.cityOf(emptyToNull(head)), country.get().name());
            }
            return new LocationLine(Countries.cityOf(trimmed), null);
        }

        // No comma: the whole line is one or the other. "Saudi Arabia" is a country, "Dubai" a city.
        return Countries.resolveSpelling(trimmed)
                .map(country -> new LocationLine(null, country.name()))
                .orElseGet(() -> new LocationLine(Countries.cityOf(trimmed), null));
    }

    public boolean isEmpty() {
        return city == null && country == null;
    }

    /** Punctuation alone names no place: a stray "," or "-" proposes neither half rather than a city. */
    private static boolean namesSomewhere(String line) {
        return line.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
