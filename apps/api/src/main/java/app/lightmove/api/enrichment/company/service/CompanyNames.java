package app.lightmove.api.enrichment.company.service;

import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Matches a remembered company name to its LinkedIn page's name ("Emaar Properties PJSC" vs "Emaar").
 * A hit counts only when it normalises exactly — "Aldar Education" is not ALDAR.
 */
final class CompanyNames {

    private static final Set<String> LEGAL_FORMS = Set.of("llc", "l.l.c", "pjsc", "psc", "plc", "ltd",
            "limited", "inc", "corp", "corporation", "co", "company", "fzco", "fze", "fz", "sa", "ag",
            "the");

    private static final Set<String> GENERIC_WORDS = Set.of("properties", "property", "group",
            "holding", "holdings", "developers", "developer", "development", "developments");

    private static final int SHORTEST_TERM = 3;

    private CompanyNames() {
    }

    /** Most specific first: without the legal form, then the core. */
    static List<String> searchTerms(String name) {
        return spellingsOf(name).stream().filter(term -> term.length() >= SHORTEST_TERM).toList();
    }

    static List<String> matchKeys(String name) {
        Set<String> keys = new LinkedHashSet<>();
        if (name != null && !name.isBlank()) {
            keys.add(name.strip().toLowerCase(Locale.ROOT));
        }
        keys.addAll(spellingsOf(name));
        return List.copyOf(keys);
    }

    /** Lower-cased, legal form dropped — the cache key. */
    static String key(String name) {
        String key = withoutLegalForm(name);
        return key.isEmpty() ? null : key;
    }

    /** The exact match, the biggest where several are. */
    static Optional<VendorCompanyRecord> best(String name, List<VendorCompanyRecord> hits) {
        Set<String> wanted = Set.copyOf(spellingsOf(name));
        return hits.stream()
                .filter(hit -> wanted.contains(withoutLegalForm(hit.companyName())))
                .max(Comparator.comparing(hit -> hit.employeesInLinkedin() == null ? 0 : hit.employeesInLinkedin()));
    }

    /** What a page's key must equal: the name without its legal form, then without its generic words. */
    static List<String> spellingsOf(String name) {
        String legal = withoutLegalForm(name);
        String core = without(words(legal), GENERIC_WORDS);
        Set<String> spellings = new LinkedHashSet<>();
        if (!legal.isEmpty()) {
            spellings.add(legal);
        }
        if (!core.isEmpty()) {
            spellings.add(core);
        }
        return List.copyOf(spellings);
    }

    private static String withoutLegalForm(String name) {
        return without(words(name), LEGAL_FORMS);
    }

    private static List<String> words(String name) {
        if (name == null) {
            return List.of();
        }
        return Arrays.stream(name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}&.]+", " ").split(" "))
                .map(word -> word.replaceAll("^\\.+|\\.+$", ""))
                .filter(word -> !word.isEmpty())
                .toList();
    }

    private static String without(List<String> words, Set<String> dropped) {
        return words.stream().filter(word -> !dropped.contains(word)).collect(Collectors.joining(" "));
    }
}
