package app.lightmove.api.enrichment.sourcing.model;

import app.lightmove.api.common.constant.Seniority;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * What one run searches for: the single words a fitting title carries — one from each of the first two
 * lists and none of the third — and the role in a sentence for the rerank. Every word is one token with
 * no whitespace, because the vendor runs a phrase query otherwise; {@link #of} enforces that whatever
 * the model answered.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SourcingSpec(List<String> seniorityWords, List<String> functionWords, List<String> excludedWords,
                           String roleSummary) {

    public static final int MAX_WORDS = 4;

    private static final Pattern NOT_A_WORD = Pattern.compile("[^\\p{L}\\p{N}&-]");

    /** Glue no title is searched by. */
    private static final Set<String> GLUE_WORDS = Set.of("of", "the", "and", "for", "to", "in", "at", "a", "an");

    /** Level and scope words a title carries that say nothing about its function. */
    private static final Set<String> LEVEL_WORDS = Set.of("head", "chief", "director", "vp", "vice", "president",
            "officer", "manager", "senior", "group", "regional", "global", "lead", "executive", "general",
            "managing", "deputy", "assistant", "division", "department", "unit", "business", "mena", "gcc",
            "uae", "ksa");

    /**
     * Words the vendor matches as substrings, so an excluded word hiding inside one of them would drop
     * every senior title carrying it — "Office" drops every Chief Executive Officer, "Intern" every
     * International.
     */
    private static final Set<String> PROTECTED_TITLE_WORDS = Set.of("officer", "director", "president",
            "executive", "chief", "head", "manager", "managing", "general", "senior", "group", "international",
            "internal", "regional", "global");

    /**
     * Abbreviations the vendor finds inside ordinary words, so they match nearly every title: "IT" is in
     * "at Kuwait", "Unit", "Security" and "Digital" (every Americana title matched it), "MD" in "SMDP".
     */
    private static final Set<String> NOISE_WORDS = Set.of("it", "md", "gm", "pr", "ai", "ea");

    private static final List<String> BOARD_WORDS = List.of("Chairman", "Board", "President", "Chief");
    private static final List<String> EXECUTIVE_WORDS = List.of("Chief", "Head", "Director", "VP");
    private static final List<String> MANAGER_WORDS = List.of("Manager", "Head", "Lead", "Senior");
    private static final List<String> SUPPORT_WORDS = List.of("Assistant", "Secretary", "Coordinator");

    /**
     * A general-management seat has no function word of its own, and "Chief" or "President" alone finds
     * every Chief Accountant and Vice President; paired, the two lists meet only in a top title.
     */
    private static final List<String> TOP_SEAT_WORDS = List.of("CEO", "Chief", "Managing", "President");
    private static final List<String> TOP_SEAT_FUNCTION_WORDS = List.of("CEO", "Executive", "Director");
    private static final List<String> TOP_SEAT_EXCLUDED_WORDS = List.of("Assistant", "Vice", "Secretary");

    public SourcingSpec {
        seniorityWords = seniorityWords == null ? List.of() : List.copyOf(seniorityWords);
        functionWords = functionWords == null ? List.of() : List.copyOf(functionWords);
        excludedWords = excludedWords == null ? List.of() : List.copyOf(excludedWords);
    }

    /**
     * Cleaned: split on whitespace, stripped of punctuation, deduplicated, at most four each; an
     * excluded word is dropped when it would also exclude a searched or senior title word.
     */
    public static SourcingSpec of(List<String> seniorityWords, List<String> functionWords,
                                  List<String> excludedWords, String roleSummary) {
        List<String> seniority = cleaned(seniorityWords);
        List<String> function = cleaned(functionWords);
        return new SourcingSpec(seniority, function, safeExclusions(excludedWords, seniority, function),
                roleSummary == null || roleSummary.isBlank() ? null : roleSummary.strip());
    }

    /**
     * What a run searches for when the model could not say: the seniority's usual words, and the
     * role title's own distinctive words as the function — or, for a title with none, the top-seat
     * pairing.
     */
    public static SourcingSpec defaultFor(String roleTitle, Seniority seniority) {
        String summary = roleTitle == null ? null : roleTitle.strip();
        List<String> functionWords = tokensOf(roleTitle == null ? List.of() : List.of(roleTitle)).stream()
                .filter(word -> !LEVEL_WORDS.contains(word.toLowerCase(Locale.ROOT)))
                .limit(MAX_WORDS)
                .toList();
        if (functionWords.isEmpty()) {
            return new SourcingSpec(TOP_SEAT_WORDS, TOP_SEAT_FUNCTION_WORDS, TOP_SEAT_EXCLUDED_WORDS, summary);
        }
        List<String> seniorityWords = seniority == null ? EXECUTIVE_WORDS : switch (seniority) {
            case BOARD -> BOARD_WORDS;
            case C_SUITE, N_MINUS_1 -> EXECUTIVE_WORDS;
            case N_MINUS_2, N_MINUS_3 -> MANAGER_WORDS;
        };
        return new SourcingSpec(seniorityWords, functionWords, SUPPORT_WORDS, summary);
    }

    private static List<String> cleaned(List<String> words) {
        return tokensOf(words).stream()
                .filter(word -> !NOISE_WORDS.contains(word.toLowerCase(Locale.ROOT)))
                .limit(MAX_WORDS)
                .toList();
    }

    private static List<String> safeExclusions(List<String> words, List<String> seniority, List<String> function) {
        List<String> kept = Stream.concat(seniority.stream(), function.stream())
                .map(word -> word.toLowerCase(Locale.ROOT))
                .toList();
        return tokensOf(words).stream()
                .filter(word -> {
                    String folded = word.toLowerCase(Locale.ROOT);
                    return Stream.concat(kept.stream(), PROTECTED_TITLE_WORDS.stream())
                            .noneMatch(searched -> searched.contains(folded));
                })
                .limit(MAX_WORDS)
                .toList();
    }

    /** Every distinct searchable word in order: split on whitespace, punctuation stripped, glue dropped. */
    private static List<String> tokensOf(List<String> words) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> kept = new ArrayList<>();
        for (String phrase : words == null ? List.<String>of() : words) {
            if (phrase == null) {
                continue;
            }
            for (String token : phrase.trim().split("\\s+")) {
                String word = NOT_A_WORD.matcher(token).replaceAll("");
                String folded = word.toLowerCase(Locale.ROOT);
                if (word.length() < 2 || GLUE_WORDS.contains(folded) || !seen.add(folded)) {
                    continue;
                }
                kept.add(word);
            }
        }
        return kept;
    }
}
