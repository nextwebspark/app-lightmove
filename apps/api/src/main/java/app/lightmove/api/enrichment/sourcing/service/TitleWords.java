package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.TitleTier;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A title read as whole words, so "CTO" is found in "CTO, DP World" and never inside "Director", "Doctor"
 * or "Sector" — the letters a substring match took for a technology chief. A searched word matches a title
 * word equal to it, or to its plural when it is four letters or more; one written with a hyphen or an
 * ampersand ("E-commerce", "M&A") matches the same run of title words.
 */
final class TitleWords {

    private static final Pattern NOT_A_LETTER = Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final int SHORTEST_PLURAL_STEM = 4;

    private TitleWords() {
    }

    static boolean containsAny(String title, List<String> words) {
        List<String> titleWords = wordsOf(title);
        return words.stream().anyMatch(word -> holdsRun(titleWords, wordsOf(word)));
    }

    /**
     * Whether a title is one {@code words} looks for: a level word and a function term, or a standalone
     * title — the function alone when the words name no level — and no excluded word.
     */
    static boolean holds(String title, SearchWords words) {
        if (title == null || title.isBlank() || containsAny(title, words.excludedWords())) {
            return false;
        }
        if (containsAny(title, words.standaloneTitles())) {
            return true;
        }
        boolean leveled = words.levelWords().isEmpty() || containsAny(title, words.levelWords());
        return leveled && containsAny(title, words.functionTerms());
    }

    static boolean holds(String title, TitleTier tier) {
        return holds(title, tier.include()) && (tier.exclude() == null || !holds(title, tier.exclude()));
    }

    /** A function term or a standalone title anywhere in it, whatever its level. */
    static boolean namesFunction(String title, SearchWords words) {
        return containsAny(title, words.functionTerms()) || containsAny(title, words.standaloneTitles());
    }

    static List<String> wordsOf(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(NOT_A_LETTER.split(text.toLowerCase(Locale.ROOT)))
                .filter(word -> !word.isEmpty())
                .toList();
    }

    private static boolean holdsRun(List<String> titleWords, List<String> searched) {
        if (searched.isEmpty()) {
            return false;
        }
        for (int start = 0; start + searched.size() <= titleWords.size(); start++) {
            int matched = 0;
            while (matched < searched.size() && sameWord(titleWords.get(start + matched), searched.get(matched))) {
                matched++;
            }
            if (matched == searched.size()) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameWord(String titleWord, String searched) {
        if (titleWord.equals(searched)) {
            return true;
        }
        return searched.length() >= SHORTEST_PLURAL_STEM
                && (titleWord.equals(searched + "s") || titleWord.equals(searched + "es"));
    }
}
