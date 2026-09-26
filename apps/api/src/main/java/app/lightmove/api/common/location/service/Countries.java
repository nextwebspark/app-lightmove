package app.lightmove.api.common.location.service;

import app.lightmove.api.common.location.model.Country;
import app.lightmove.api.common.service.ClasspathJsonLoader;
import app.lightmove.api.core.text.service.TextUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Resolves every spelling of a country to one ISO code and English name, canonicalised on write.
 * Static so compact constructors can call it; an unknown country is kept as typed. A bare alpha-2
 * code is accepted only where no name claims it — "IN", "IT", "NO" are ordinary words.
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
        // Passed rather than read off the field: a reordering that left it empty would fail silently.
        Map<String, String> spellings = codesByName(file, names);
        CODE_BY_SPELLING = Map.copyOf(spellings);
        names.keySet().forEach(code -> spellings.putIfAbsent(code.toLowerCase(Locale.ROOT), code));
        CODE_BY_NAME = Map.copyOf(spellings);
        CITY_BY_NAME = citiesByName(file);
    }

    private Countries() {
    }

    public static Optional<Country> resolve(String spelling) {
        if (spelling == null) {
            return Optional.empty();
        }
        String code = CODE_BY_NAME.get(normalise(spelling));
        return code == null ? Optional.empty() : Optional.of(new Country(code, NAME_BY_CODE.get(code)));
    }

    /** The catalog's English name where it knows one, else the caller's own, trimmed. */
    public static String nameOf(String spelling) {
        String trimmed = TextUtils.collapseWhitespaceToNull(spelling);
        return trimmed == null ? null : resolve(trimmed).map(Country::name).orElse(trimmed);
    }

    /**
     * Never accepts a bare alpha-2 code: 26 US state and Canadian province abbreviations are also ISO
     * codes, and "Chicago, IL" filed its companies under Israel.
     */
    public static Optional<Country> resolveSpelling(String spelling) {
        if (spelling == null) {
            return Optional.empty();
        }
        String code = CODE_BY_SPELLING.get(normalise(spelling));
        return code == null ? Optional.empty() : Optional.of(new Country(code, NAME_BY_CODE.get(code)));
    }

    public static String codeOf(String spelling) {
        return resolve(spelling).map(Country::code).orElse(null);
    }

    public static Optional<String> nameOfCode(String isoCode) {
        if (isoCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(NAME_BY_CODE.get(isoCode.trim().toUpperCase(Locale.ROOT)));
    }

    /** A city in the catalog's casing ("khobar" → "Al Khobar"); an unknown city keeps its spelling. */
    public static String cityOf(String spelling) {
        String trimmed = TextUtils.collapseWhitespaceToNull(spelling);
        if (trimmed == null) {
            return null;
        }
        String known = CITY_BY_NAME.get(normalise(trimmed));
        return known == null ? trimmed : known;
    }

    /** Every country with the spellings that find it, so the picker searches what writes canonicalise. */
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
        String collapsed = TextUtils.collapseWhitespaceToNull(value);
        return collapsed == null ? "" : collapsed.toLowerCase(Locale.ROOT);
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
                // A spelling shared by two countries would silently refile every row carrying it.
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
        return ClasspathJsonLoader.load(
                new ObjectMapper(), RESOURCE, new TypeReference<LinkedHashMap<String, Entry>>() {});
    }

    /** Only what the JDK's catalog does not already answer. */
    private record Entry(List<String> aliases, Map<String, String> cities) {
        Entry {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            cities = cities == null ? Map.of() : Map.copyOf(cities);
        }
    }
}
