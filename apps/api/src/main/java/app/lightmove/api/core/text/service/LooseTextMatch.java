package app.lightmove.api.core.text.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Whether a proposed line says what an expected one does, for scoring a model's reading against a labelled answer:
 * case, punctuation and spacing never count, and a proposal that shortens the expected line still matches it.
 */
public final class LooseTextMatch {

    private LooseTextMatch() {
    }

    /** Lower case, with every run of anything but letters and digits as one space. Null is the empty string. */
    public static String normalised(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    /**
     * Equal, one inside the other, or most of the proposal's own words found in the expected line — a
     * reading shortens a document's sentence into a few words of it, so the overlap is measured from the
     * proposal's side. Both sides are taken as already {@link #normalised}.
     */
    public static boolean matches(String expected, String proposed) {
        if (expected.isEmpty() || proposed.isEmpty()) {
            return false;
        }
        if (expected.equals(proposed) || expected.contains(proposed) || proposed.contains(expected)) {
            return true;
        }
        Set<String> expectedWords = significantWords(expected);
        Set<String> proposedWords = significantWords(proposed);
        Set<String> shared = new HashSet<>(proposedWords);
        shared.retainAll(expectedWords);
        return shared.size() >= 2 && (double) shared.size() / proposedWords.size() >= 0.6;
    }

    private static Set<String> significantWords(String value) {
        return Arrays.stream(value.split(" ")).filter(word -> word.length() > 2).collect(Collectors.toSet());
    }
}
