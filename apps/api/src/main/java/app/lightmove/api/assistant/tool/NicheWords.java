package app.lightmove.api.assistant.tool;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** The single words a LinkedIn search keys on, since its {@code includes} runs ten seconds on a phrase. */
final class NicheWords {

    static final int MAX_WORDS = 4;

    /** Words of a niche keyword that say nothing about the niche on their own. */
    private static final Set<String> GENERIC_WORDS = Set.of("services", "service", "solutions", "company",
            "companies", "group", "business", "businesses", "customer", "customers", "experience", "brand", "brands",
            "market", "markets", "digital", "management", "family", "consumer", "consumers", "products", "product",
            "international", "local", "online", "luxury", "premium", "high", "end", "high-end", "retail", "trading",
            "quality", "global", "regional", "middle", "east", "innovation", "development", "industry");

    private static final Pattern WORD_BREAK = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern PLURAL_ES = Pattern.compile(".*(ch|sh|x|ss)es");

    private NicheWords() {
    }

    /** The words most niche keywords share — "watch" in "luxury watches", "watch retail" and "watch repair". */
    static List<String> of(List<String> niche) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String keyword : niche) {
            Set<String> words = new LinkedHashSet<>();
            for (String word : WORD_BREAK.split(keyword.toLowerCase(Locale.ROOT))) {
                String stem = singular(word);
                if (stem.length() >= 4 && !GENERIC_WORDS.contains(stem) && !GENERIC_WORDS.contains(word)) {
                    words.add(stem);
                }
            }
            words.forEach(word -> counts.merge(word, 1, Integer::sum));
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(MAX_WORDS)
                .toList();
    }

    /** What the model asked for, as singular words. */
    static List<String> stemsOf(List<String> words) {
        return words.stream()
                .filter(Objects::nonNull)
                .flatMap(word -> Stream.of(WORD_BREAK.split(word.toLowerCase(Locale.ROOT))))
                .map(NicheWords::singular)
                .filter(word -> word.length() >= 3)
                .distinct()
                .limit(MAX_WORDS)
                .toList();
    }

    /** "watches" → "watch", "distributors" → "distributor": LinkedIn's {@code includes} then finds both. */
    static String singular(String word) {
        if (word.length() > 4 && PLURAL_ES.matcher(word).matches()) {
            return word.substring(0, word.length() - 2);
        }
        if (word.length() > 4 && word.endsWith("s") && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }
}
