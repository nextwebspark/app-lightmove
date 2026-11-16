package app.lightmove.api.candidate.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Whether two spellings of a name could be one person: the same surname and a shared first initial, read through
 * "Smith, John" order, titles and suffixes, accents and name particles. Last word alone once missed "John Smith Jr."
 */
final class PersonNames {

    private static final Set<String> TITLES_AND_SUFFIXES = Set.of("dr", "mr", "mrs", "ms", "miss", "prof", "eng",
            "engr", "sheikh", "sheikha", "sir", "dame", "hon", "jr", "sr", "ii", "iii", "iv", "phd", "mba", "cfa",
            "cpa", "acca", "frcs", "pmp", "esq");

    /** Dropped, and peeled off a surname they are glued to, so "Al Suwaidi", "Al-Suwaidi", "AlSuwaidi" and "Suwaidi" agree. */
    private static final Set<String> PARTICLES = Set.of("al", "el", "bin", "bint", "ibn", "abu", "van", "von", "de",
            "da", "di", "du", "le", "la");

    /** Only these are written glued ("AlSuwaidi"); peeling "de" or "la" would make Delaney agree with Laney. */
    private static final Set<String> GLUED_PARTICLES = Set.of("al", "el", "bin", "ibn", "abu");

    private PersonNames() {
    }

    /** A side with no name blocks nothing: some doors send an address alone. */
    static boolean agree(String one, String other) {
        List<String> first = wordsOf(one);
        List<String> second = wordsOf(other);
        if (first.isEmpty() || second.isEmpty()) {
            return true;
        }
        if (!surnameSpellings(first.getLast()).stream().anyMatch(surnameSpellings(second.getLast())::contains)) {
            return false;
        }
        boolean bothGiven = first.size() > 1 && second.size() > 1;
        return !bothGiven || first.getFirst().charAt(0) == second.getFirst().charAt(0);
    }

    private static List<String> wordsOf(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return List.of();
        }
        List<String> words = meaningfulWords(inGivenNameOrder(fullName));
        List<String> named = words.stream().filter(word -> !PARTICLES.contains(word)).toList();
        return named.isEmpty() ? words : named;
    }

    private static Set<String> surnameSpellings(String surname) {
        return GLUED_PARTICLES.stream()
                .filter(particle -> surname.startsWith(particle) && surname.length() - particle.length() >= 3)
                .map(particle -> surname.substring(particle.length()))
                .collect(Collectors.toCollection(() -> new HashSet<>(Set.of(surname))));
    }

    /** "Smith, John" is John Smith; "John Smith, Jr." keeps its order, its comma holding only a suffix. */
    private static String inGivenNameOrder(String fullName) {
        int comma = fullName.indexOf(',');
        if (comma < 0) {
            return fullName;
        }
        String before = fullName.substring(0, comma);
        String after = fullName.substring(comma + 1);
        return meaningfulWords(after).isEmpty() ? before : after + " " + before;
    }

    private static List<String> meaningfulWords(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('-', ' ')
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ");
        return Arrays.stream(plain.trim().split("\\s+"))
                .filter(word -> !word.isEmpty() && !TITLES_AND_SUFFIXES.contains(word))
                .toList();
    }
}
