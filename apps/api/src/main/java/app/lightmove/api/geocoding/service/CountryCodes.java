package app.lightmove.api.geocoding.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Country names as this application spells them — the Apollo export's, the plugin's, a spreadsheet's
 * — to the ISO 3166-1 alpha-2 code a geocoding vendor filters by.
 *
 * <p>The JDK's own catalog answers nearly everything; the aliases cover what a researcher actually
 * types, and a bare alpha-2 code is accepted too. Unknown is an answer, not an error: the caller
 * then asks the vendor by name alone.
 */
public final class CountryCodes {

    private static final Map<String, String> BY_NAME = build();

    private CountryCodes() {
    }

    public static Optional<String> isoCodeOf(String countryName) {
        if (countryName == null) {
            return Optional.empty();
        }
        String normalised = countryName.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return Optional.ofNullable(BY_NAME.get(normalised));
    }

    private static Map<String, String> build() {
        Map<String, String> byName = new HashMap<>();
        for (String code : Locale.getISOCountries()) {
            String name = Locale.of("", code).getDisplayCountry(Locale.ENGLISH);
            if (!name.isBlank()) {
                byName.put(name.toLowerCase(Locale.ROOT), code);
            }
        }
        // Bare codes second and only where nothing claimed the spelling: a dozen of them ("IN", "IT",
        // "NO", "ME") are ordinary words a spreadsheet's country column carries, and a display name is
        // always the better reading of one.
        for (String code : Locale.getISOCountries()) {
            byName.putIfAbsent(code.toLowerCase(Locale.ROOT), code);
        }
        byName.put("uae", "AE");
        byName.put("u.a.e.", "AE");
        byName.put("u.a.e", "AE");
        byName.put("emirates", "AE");
        byName.put("ksa", "SA");
        byName.put("saudi", "SA");
        byName.put("kingdom of saudi arabia", "SA");
        byName.put("uk", "GB");
        byName.put("united kingdom", "GB");
        byName.put("usa", "US");
        byName.put("united states of america", "US");
        return Map.copyOf(byName);
    }
}
