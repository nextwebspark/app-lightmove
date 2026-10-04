package app.lightmove.api.position.model;

import app.lightmove.api.common.location.model.Country;
import app.lightmove.api.common.location.service.Countries;
import java.util.Optional;

/**
 * One line of prose naming where a role sits, split into the brief's city and country (V66).
 *
 * <p>The catalog decides, never the comma, via {@link Countries#resolveSpelling} rather than
 * {@link Countries#resolve}: it refuses a bare alpha-2 code, so "Chicago, IL" stays a city, not Israel.
 *
 * @param country the catalog's own spelling, or null where none was recognised
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

        return Countries.resolveSpelling(trimmed)
                .map(country -> new LocationLine(null, country.name()))
                .orElseGet(() -> new LocationLine(Countries.cityOf(trimmed), null));
    }

    public boolean isEmpty() {
        return city == null && country == null;
    }

    private static boolean namesSomewhere(String line) {
        return line.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
