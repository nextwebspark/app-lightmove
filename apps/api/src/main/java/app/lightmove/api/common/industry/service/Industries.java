package app.lightmove.api.common.industry.service;

import app.lightmove.api.common.industry.model.ResolvedIndustry;
import app.lightmove.api.common.service.ClasspathJsonLoader;
import app.lightmove.api.core.text.service.SuppliedText;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Resolves every spelling of an industry — vendor, spreadsheet, typed — to the universe's LinkedIn V1
 * label; Bright Data answers in V2. Static so {@code CapturedCompanyDetails}' compact constructor can
 * call it. An unknown industry is kept as typed.
 *
 * <p>The map is keyed on LinkedIn's industry id, shared by V1 and V2 — except id 25, which V2 gave to
 * the {@code Manufacturing} root where V1 had {@code Consumer Goods}.
 */
public final class Industries {

    private static final String RESOURCE = "data/industry-map.json";
    private static final Set<String> LOWER_CASE_WORDS = Set.of("&", "and", "of", "the", "for", "in");

    private static final Map<String, String> INDUSTRY_BY_SPELLING;
    private static final Map<String, ResolvedIndustry> RESOLVED_BY_INDUSTRY;

    static {
        Map<String, String> bySpelling = new HashMap<>();
        Map<String, ResolvedIndustry> byIndustry = new HashMap<>();
        read().forEach((code, entry) -> {
            for (String spelling : entry.spellings()) {
                String previous = bySpelling.put(fold(spelling), entry.apollo());
                // A spelling shared by two industries would silently refile every row carrying it.
                if (previous != null && !previous.equals(entry.apollo())) {
                    throw new IllegalStateException("%s gives '%s' to both '%s' and '%s'"
                            .formatted(RESOURCE, spelling, previous, entry.apollo()));
                }
            }
            byIndustry.put(entry.apollo(), new ResolvedIndustry(
                    entry.apollo(), linkedInCode(code), entry.v2Label(), entry.sectorGroup()));
        });
        INDUSTRY_BY_SPELLING = Map.copyOf(bySpelling);
        RESOLVED_BY_INDUSTRY = Map.copyOf(byIndustry);
    }

    /** One entry is Apollo's own label, keyed by name, with no LinkedIn code. */
    private static Integer linkedInCode(String key) {
        return key.chars().allMatch(Character::isDigit) ? Integer.valueOf(key) : null;
    }

    private Industries() {
    }

    /** The universe's label where the map knows one, else the caller's own, trimmed; blank → null. */
    public static String nameOf(String spelling) {
        String trimmed = SuppliedText.collapseWhitespaceToNull(spelling);
        if (trimmed == null) {
            return null;
        }
        String known = INDUSTRY_BY_SPELLING.get(fold(trimmed));
        return known == null ? trimmed : known;
    }

    /**
     * All four forms of one industry from a single call, so a row's columns cannot disagree. An
     * unresolvable spelling keeps its own text with the derived fields null.
     */
    public static ResolvedIndustry resolve(String spelling) {
        String label = nameOf(spelling);
        if (label == null) {
            return null;
        }
        ResolvedIndustry known = RESOLVED_BY_INDUSTRY.get(label);
        return known != null ? known : new ResolvedIndustry(label, null, null, null);
    }

    /** Whether the universe publishes this industry, however it is spelled. */
    public static boolean isKnown(String spelling) {
        String trimmed = SuppliedText.collapseWhitespaceToNull(spelling);
        return trimmed != null && INDUSTRY_BY_SPELLING.containsKey(fold(trimmed));
    }

    /** Title-cases the universe's lower-case label; a label with any capital keeps its casing. */
    public static String displayNameOf(String label) {
        if (label == null || !label.equals(label.toLowerCase(Locale.ROOT))) {
            return label;
        }
        String[] words = label.split(" ");
        for (int index = 0; index < words.length; index++) {
            String word = words[index];
            if (word.isEmpty() || (index > 0 && LOWER_CASE_WORDS.contains(word))) {
                continue;
            }
            words[index] = Character.toUpperCase(word.charAt(0)) + word.substring(1);
        }
        return String.join(" ", words);
    }

    /**
     * Lower-cased, diacritics dropped, {@code and} read as {@code &}, separators removed — not
     * collapsed, since "Non-Profit" and "nonprofit" must fold together.
     */
    private static String fold(String value) {
        String withoutMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String ampersanded = withoutMarks.toLowerCase(Locale.ROOT).replace(" and ", " & ");
        return ampersanded.replaceAll("[^a-z0-9]+", "");
    }

    private static Map<String, Entry> read() {
        return ClasspathJsonLoader.load(
                new ObjectMapper(), RESOURCE, new TypeReference<LinkedHashMap<String, Entry>>() {});
    }

    /** {@code curated} is read by nobody but must be accepted or the file fails to parse. */
    private record Entry(String apollo, String v2Label, String sectorGroup, List<String> aliases,
                         Boolean curated) {
        Entry {
            if (apollo == null || apollo.isBlank()) {
                throw new IllegalStateException(RESOURCE + " has an entry with no apollo label");
            }
            if (sectorGroup == null || sectorGroup.isBlank()) {
                throw new IllegalStateException(RESOURCE + " gives '" + apollo + "' no sector");
            }
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }

        List<String> spellings() {
            return Stream.concat(Stream.of(apollo), aliases.stream()).toList();
        }
    }
}
