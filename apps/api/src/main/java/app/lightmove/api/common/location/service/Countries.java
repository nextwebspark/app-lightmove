package app.lightmove.api.common.location.service;

import app.lightmove.api.common.location.model.Country;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Every spelling of a country this application has to read — an Apollo export's, a vendor's, a
 * spreadsheet's — resolved to one code and one English name.
 *
 * <p><b>Called on the way in, not only on the way out.</b> A country is canonicalised where it is
 * written, so the Strategy filter's exact match, a grid's grouping and the map's country branch all
 * compare one value. Resolving at render time would leave the database holding six spellings.
 *
 * <p>The JDK's own catalog answers nearly everything and its English names match the universe's
 * verbatim, so the classpath file carries only what the JDK does not know. A bare alpha-2 code is
 * accepted too, but only where no display name claimed the spelling — a dozen of them ("IN", "IT",
 * "NO", "ME") are ordinary words a spreadsheet's country column carries.
 *
 * <p><b>Static rather than a {@code @Component}</b>: the seam has to be reachable from
 * {@code CapturedCompanyDetails} and {@code CandidateDetails}, whose compact constructors already
 * normalise and cannot be injected into.
 *
 * <p>Unknown is an answer, not an error — a country nobody can resolve is kept as it was typed.
 */
public final class Countries {

    private static final String RESOURCE = "data/country-aliases.json";

    private static final Map<String, String> CODE_BY_NAME;
    private static final Map<String, String> CODE_BY_SPELLING;
    private static final Map<String, String> NAME_BY_CODE;
    private static final Map<String, String> CITY_BY_NAME;

    static {
        Map<String, Entry> file = read();
        Map<String, String> names = namesByCode();
        NAME_BY_CODE = names;
        // Passed rather than read back off the field: these assignments are order-dependent, and a
        // reordering that left the map empty would fail nothing loudly — every country would simply
        // stop resolving.
        Map<String, String> spellings = codesByName(file, names);
        CODE_BY_SPELLING = Map.copyOf(spellings);
        // One map grown into the other: the two differ by the bare-code pass alone.
        names.keySet().forEach(code -> spellings.putIfAbsent(code.toLowerCase(Locale.ROOT), code));
        CODE_BY_NAME = Map.copyOf(spellings);
        CITY_BY_NAME = citiesByName(file);
    }

    private Countries() {
    }

    /** The country a spelling names, or empty where the catalog has never heard of it. */
    public static Optional<Country> resolve(String spelling) {
        if (spelling == null) {
            return Optional.empty();
        }
        String code = CODE_BY_NAME.get(normalise(spelling));
        return code == null ? Optional.empty() : Optional.of(new Country(code, NAME_BY_CODE.get(code)));
    }

    /**
     * The spelling to store: the catalog's English name where it knows one, and otherwise the caller's
     * own, trimmed. Null in, null out.
     */
    public static String nameOf(String spelling) {
        String trimmed = trimmed(spelling);
        return trimmed == null ? null : resolve(trimmed).map(Country::name).orElse(trimmed);
    }

    /**
     * The country a spelling names, accepting only a written-out name or one of the catalog's
     * abbreviations — never a bare alpha-2 code.
     *
     * <p>26 US state and Canadian province abbreviations are also ISO country codes, so reading the
     * tail of "Chicago, IL" as a country files those companies under Israel. A line of prose is where
     * that misreading happens, so it asks here.
     */
    public static Optional<Country> resolveSpelling(String spelling) {
        if (spelling == null) {
            return Optional.empty();
        }
        String code = CODE_BY_SPELLING.get(normalise(spelling));
        return code == null ? Optional.empty() : Optional.of(new Country(code, NAME_BY_CODE.get(code)));
    }

    /** The ISO alpha-2 code a spelling names, or null. */
    public static String codeOf(String spelling) {
        return resolve(spelling).map(Country::code).orElse(null);
    }

    /** The one English name for a code — what a stored code reads back as. */
    public static Optional<String> nameOfCode(String isoCode) {
        if (isoCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(NAME_BY_CODE.get(isoCode.trim().toUpperCase(Locale.ROOT)));
    }

    /**
     * A city in the catalog's casing — "khobar" and "Al Khobar" are one place. One the catalog does
     * not carry keeps its own spelling: cities are not a closed vocabulary.
     */
    public static String cityOf(String spelling) {
        String trimmed = trimmed(spelling);
        if (trimmed == null) {
            return null;
        }
        String known = CITY_BY_NAME.get(normalise(trimmed));
        return known == null ? trimmed : known;
    }

    /**
     * Every country with the spellings that find it, so the picker searches on the same abbreviations
     * this canonicalises on write rather than a list of its own.
     */
    public static List<Country> all() {
        Map<String, List<String>> aliases = new HashMap<>();
        CODE_BY_SPELLING.forEach((spelling, code) ->
                aliases.computeIfAbsent(code, ignored -> new ArrayList<>()).add(spelling));
        return NAME_BY_CODE.entrySet().stream()
                .map(entry -> new Country(entry.getKey(), entry.getValue(),
                        spellingsOf(aliases, entry.getKey())))
                .sorted((left, right) -> left.name().compareToIgnoreCase(right.name()))
                .toList();
    }

    private static List<String> spellingsOf(Map<String, List<String>> aliases, String code) {
        List<String> spellings = new ArrayList<>(aliases.getOrDefault(code, List.of()));
        spellings.add(code.toLowerCase(Locale.ROOT));
        spellings.sort(null);
        return List.copyOf(spellings);
    }

    static String normalise(String value) {
        return trimmed(value) == null ? "" : trimmed(value).toLowerCase(Locale.ROOT);
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String collapsed = value.trim().replaceAll("\\s+", " ");
        return collapsed.isEmpty() ? null : collapsed;
    }

    private static Map<String, String> namesByCode() {
        Map<String, String> byCode = new HashMap<>();
        for (String code : Locale.getISOCountries()) {
            String name = Locale.of("", code).getDisplayCountry(Locale.ENGLISH);
            if (!name.isBlank()) {
                byCode.put(code, name);
            }
        }
        return Map.copyOf(byCode);
    }

    private static Map<String, String> codesByName(Map<String, Entry> file, Map<String, String> names) {
        Map<String, String> byName = new HashMap<>();
        names.forEach((code, name) -> byName.put(normalise(name), code));
        file.forEach((code, entry) -> {
            if (!names.containsKey(code)) {
                throw new IllegalStateException("%s names '%s', which is not an ISO country".formatted(RESOURCE, code));
            }
            for (String alias : entry.aliases()) {
                String previous = byName.put(normalise(alias), code);
                // A spelling that already named another country would silently refile every row
                // carrying it, which is exactly the confusion this catalog exists to end.
                if (previous != null && !previous.equals(code)) {
                    throw new IllegalStateException("%s gives '%s' to both %s and %s"
                            .formatted(RESOURCE, alias, previous, code));
                }
            }
        });
        return byName;
    }

    private static Map<String, String> citiesByName(Map<String, Entry> file) {
        Map<String, String> byName = new HashMap<>();
        file.values().forEach(entry -> entry.cities().forEach((spelling, city) ->
                byName.put(normalise(spelling), city)));
        return Map.copyOf(byName);
    }

    private static Map<String, Entry> read() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return new ObjectMapper().readValue(in, new TypeReference<LinkedHashMap<String, Entry>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Could not load " + RESOURCE, e);
        }
    }

    /** One country's entry in the file: what the JDK's catalog does not already answer. */
    private record Entry(List<String> aliases, Map<String, String> cities) {
        Entry {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            cities = cities == null ? Map.of() : Map.copyOf(cities);
        }
    }
}
