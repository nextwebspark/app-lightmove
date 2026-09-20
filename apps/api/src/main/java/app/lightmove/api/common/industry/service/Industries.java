package app.lightmove.api.common.industry.service;

import app.lightmove.api.common.industry.model.ResolvedIndustry;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Every spelling of an industry this application has to read — a vendor's, a spreadsheet's, a typed
 * one — resolved to the one label the company universe publishes.
 *
 * <p>The universe's {@code industry} column is LinkedIn's <b>legacy V1</b> vocabulary, lower-cased
 * with {@code and} written {@code &}: 147 labels plus one Apollo stray. Bright Data answers in
 * LinkedIn's <b>V2</b> vocabulary, 434 labels deep in a hierarchy. Without this the two sit in one
 * column and every screen that groups by it — the report's sector chapter above all — counts
 * "Software Development" and "computer software" as two sectors.
 *
 * <p><b>Called on the way in, not only on the way out</b>, for {@link
 * app.lightmove.api.common.location.service.Countries}' reasons: the Strategy filter's exact match,
 * the grid's grouping and the report's tally all compare one value, and resolving at render time
 * would leave the database holding both vocabularies.
 *
 * <p><b>Static rather than a {@code @Component}</b>: the seam has to be reachable from
 * {@code CapturedCompanyDetails}, whose compact constructor already normalises and cannot be
 * injected into.
 *
 * <p>Unknown is an answer, not an error — an industry nobody can resolve is kept as it was typed.
 *
 * <p>The file is keyed on LinkedIn's industry id because V2 renamed V1's industries <i>in place, at
 * the same ids</i>, which is what makes the map derivable rather than guessed. Two things about it
 * are worth knowing before editing: the key is that shared id, and is what {@link #resolve} answers
 * with — lookup is still by folded spelling — and <b>id 25 is the one place the rule breaks</b>, because V2
 * gave it to the {@code Manufacturing} root while V1 had it as {@code Consumer Goods}. Entries
 * carrying {@code "curated": true} are the placements no rule could make.
 */
public final class Industries {

    private static final String RESOURCE = "data/industry-map.json";

    private static final Map<String, String> INDUSTRY_BY_SPELLING;
    private static final Map<String, ResolvedIndustry> RESOLVED_BY_INDUSTRY;

    static {
        Map<String, String> bySpelling = new HashMap<>();
        Map<String, ResolvedIndustry> byIndustry = new HashMap<>();
        read().forEach((code, entry) -> {
            for (String spelling : entry.spellings()) {
                String previous = bySpelling.put(fold(spelling), entry.apollo());
                // A spelling that already named another industry would silently refile every row
                // carrying it, which is the drift this map exists to end.
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

    /**
     * The key is LinkedIn's industry id, which V1 and V2 share. One entry is Apollo's own label,
     * which LinkedIn never had, so it is keyed by name and has no code.
     */
    private static Integer linkedInCode(String key) {
        return key.chars().allMatch(Character::isDigit) ? Integer.valueOf(key) : null;
    }

    private Industries() {
    }

    /**
     * The spelling to store: the universe's own label where the map knows one, and otherwise the
     * caller's own, trimmed. Null or blank in, null out.
     */
    public static String nameOf(String spelling) {
        String trimmed = trimmed(spelling);
        if (trimmed == null) {
            return null;
        }
        String known = INDUSTRY_BY_SPELLING.get(fold(trimmed));
        return known == null ? trimmed : known;
    }

    /**
     * Every form of one industry at once — the label to store, LinkedIn's id, its V2 name and its
     * sector — so a row carrying all four gets them from a single call and they cannot disagree.
     *
     * <p>A spelling the map does not carry answers with the caller's own text and nothing else, the
     * same way {@link #nameOf} keeps it: null in the derived fields is "nobody could resolve this",
     * which is a different fact from any group it might have been filed under.
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
        String trimmed = trimmed(spelling);
        return trimmed != null && INDUSTRY_BY_SPELLING.containsKey(fold(trimmed));
    }

    /**
     * The comparison key: lower-cased, diacritics dropped, {@code and} read as {@code &}, and every
     * remaining separator removed.
     *
     * <p>Separators go rather than collapse because the two vocabularies disagree about them on the
     * same industry — V1 writes "Non-Profit Organization Management" where the universe publishes
     * "nonprofit organization management", and a fold that kept the hyphen as a space would file
     * them apart.
     */
    private static String fold(String value) {
        String withoutMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String ampersanded = withoutMarks.toLowerCase(Locale.ROOT).replace(" and ", " & ");
        return ampersanded.replaceAll("[^a-z0-9]+", "");
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String collapsed = value.trim().replaceAll("\\s+", " ");
        return collapsed.isEmpty() ? null : collapsed;
    }

    private static Map<String, Entry> read() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return new ObjectMapper().readValue(in, new TypeReference<LinkedHashMap<String, Entry>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Could not load " + RESOURCE, e);
        }
    }

    /**
     * One industry's entry: the label the universe publishes, and every spelling that finds it.
     * {@code curated} is read by nobody — it marks the placements a reviewer should argue with, and
     * the record has to accept it or the file fails to parse. Boxed because it is absent from most
     * entries, and Jackson has no value to give a missing primitive.
     */
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
