package app.lightmove.api.core.email.render;

import java.util.List;
import java.util.Locale;

/**
 * "a" or "an" before a role title a person reads, by how the title sounds: "an Operations Director", "an HR Director",
 * "a Unit Head". Mirrored by {@code withArticle} in the SPA's {@code consentPreview.ts}, so the preview matches.
 */
public final class IndefiniteArticle {

    private static final String VOWELS = "aeiou";
    private static final String VOWEL_SOUNDING_LETTERS = "AEFHILMNORSX";
    private static final List<String> CONSONANT_SOUNDING_STARTS = List.of("eu", "one", "uni", "use", "usu", "ewe");

    private IndefiniteArticle() {
    }

    public static String before(String noun) {
        String trimmed = noun == null ? "" : noun.strip();
        return (soundsLikeVowel(trimmed) ? "an " : "a ") + trimmed;
    }

    private static boolean soundsLikeVowel(String noun) {
        if (noun.isEmpty()) {
            return false;
        }
        String firstWord = noun.split("[\\s/-]", 2)[0];
        if (isAcronym(firstWord)) {
            return VOWEL_SOUNDING_LETTERS.indexOf(firstWord.charAt(0)) >= 0;
        }
        String lower = firstWord.toLowerCase(Locale.ROOT);
        if (CONSONANT_SOUNDING_STARTS.stream().anyMatch(lower::startsWith)) {
            return false;
        }
        return VOWELS.indexOf(lower.charAt(0)) >= 0;
    }

    private static boolean isAcronym(String word) {
        return word.length() > 1 && word.chars().allMatch(character -> Character.isUpperCase(character)
                || Character.isDigit(character));
    }
}
