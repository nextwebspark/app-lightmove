package app.lightmove.api.report.service;

import app.lightmove.api.common.location.model.Country;
import app.lightmove.api.common.location.service.Countries;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One spelling per nationality, so "Saudi", "saudi arabian" and "KSA" count as one group. A
 * nationality is free text on a candidate, typed by a researcher or read off a profile, and a
 * breakdown over the raw column would list the same people three times under three headings.
 *
 * <p>Two routes to a demonym: a spelling the table knows, or a country the catalog resolves — a
 * profile often says "Egypt" where it means Egyptian. A spelling neither knows keeps its own,
 * title-cased. The Gulf six are named because localisation rules turn on exactly that line.
 */
final class NationalityCatalog {

    private static final Map<String, String> DEMONYM_BY_COUNTRY_CODE = Map.ofEntries(
            Map.entry("SA", "Saudi"), Map.entry("AE", "Emirati"), Map.entry("KW", "Kuwaiti"),
            Map.entry("QA", "Qatari"), Map.entry("OM", "Omani"), Map.entry("BH", "Bahraini"),
            Map.entry("EG", "Egyptian"), Map.entry("LB", "Lebanese"), Map.entry("JO", "Jordanian"),
            Map.entry("SY", "Syrian"), Map.entry("IQ", "Iraqi"), Map.entry("IN", "Indian"),
            Map.entry("PK", "Pakistani"), Map.entry("GB", "British"), Map.entry("US", "American"),
            Map.entry("FR", "French"), Map.entry("DE", "German"), Map.entry("TR", "Turkish"));

    private static final Map<String, String> DEMONYM_BY_SPELLING = Map.ofEntries(
            Map.entry("saudi", "Saudi"), Map.entry("saudi arabian", "Saudi"), Map.entry("saudi national", "Saudi"),
            Map.entry("emirati", "Emirati"), Map.entry("uae national", "Emirati"),
            Map.entry("kuwaiti", "Kuwaiti"), Map.entry("qatari", "Qatari"), Map.entry("omani", "Omani"),
            Map.entry("bahraini", "Bahraini"), Map.entry("egyptian", "Egyptian"), Map.entry("lebanese", "Lebanese"),
            Map.entry("jordanian", "Jordanian"), Map.entry("syrian", "Syrian"), Map.entry("iraqi", "Iraqi"),
            Map.entry("indian", "Indian"), Map.entry("pakistani", "Pakistani"), Map.entry("british", "British"),
            Map.entry("english", "British"), Map.entry("american", "American"), Map.entry("french", "French"),
            Map.entry("german", "German"), Map.entry("turkish", "Turkish"));

    private static final Set<String> GCC_DEMONYMS =
            Set.of("Saudi", "Emirati", "Kuwaiti", "Qatari", "Omani", "Bahraini");

    private NationalityCatalog() {
    }

    /** The one spelling to count under, or null for a blank. */
    static String demonymOf(String spelling) {
        if (spelling == null || spelling.isBlank()) {
            return null;
        }
        String normalised = spelling.trim().toLowerCase(Locale.ROOT);
        String known = DEMONYM_BY_SPELLING.get(normalised);
        if (known != null) {
            return known;
        }
        return Countries.resolve(normalised)
                .map(Country::code)
                .map(DEMONYM_BY_COUNTRY_CODE::get)
                .orElseGet(() -> titleCase(spelling.trim()));
    }

    static boolean isGcc(String demonym) {
        return demonym != null && GCC_DEMONYMS.contains(demonym);
    }

    private static String titleCase(String words) {
        StringBuilder out = new StringBuilder(words.length());
        boolean startOfWord = true;
        for (char letter : words.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append(startOfWord ? Character.toUpperCase(letter) : letter);
            startOfWord = letter == ' ' || letter == '-';
        }
        return out.toString();
    }
}
