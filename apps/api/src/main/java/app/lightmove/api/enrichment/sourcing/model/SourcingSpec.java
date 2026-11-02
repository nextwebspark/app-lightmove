package app.lightmove.api.enrichment.sourcing.model;

import app.lightmove.api.common.constant.Seniority;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * What one run searches for: the single words a fitting title carries — one from each of the first two
 * lists and none of the third — and the role in a sentence. Every word is one token with
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
     * "at Kuwait", "Unit", "Security" and "Digital" (every Americana title matched it), "MD" in "SMDP",
     * "HR" in "Threat" and "Chrome". A function abbreviation is searched by its spellings instead.
     */
    private static final Set<String> NOISE_WORDS = Set.of("it", "md", "gm", "pr", "ai", "ea", "hr");

    /**
     * Seat abbreviations a substring match finds inside ordinary title words, though a whole-word one
     * never does: "CTO" in Director, Doctor and Sector, "COO" in Coordinator, "CCO" in Account, "CRO" in
     * Micro. Only an index matching substrings is spared them.
     */
    private static final Set<String> HIDDEN_IN_LONGER_WORDS = Set.of("cto", "coo", "cco", "cro");

    /** The function abbreviations among the noise words, and the words that stand in for each. */
    private static final Map<String, List<String>> ABBREVIATION_SPELLINGS = Map.of(
            "HR", List.of("Human", "People", "CHRO"),
            "IT", List.of("Technology", "CIO", "CTO"),
            "PR", List.of("Communications"));

    /**
     * Words many functions' titles carry — "Business Development", "Surgical Infrastructure", "Medical
     * Systems" — so they never prove which function a title is.
     */
    private static final Set<String> SHARED_FUNCTION_WORDS = Set.of("development", "system", "solution",
            "infrastructure", "network", "service", "project", "program", "programme", "operation", "operational",
            "business", "strategy", "strategic", "transformation", "planning", "management", "support",
            "application", "engineering", "technical", "innovation", "excellence", "performance", "quality",
            "commercial", "corporate", "administration", "process", "product", "enterprise", "security", "growth");

    /** The general-management seat's abbreviations, which a role title may be alone. */
    private static final Set<String> TOP_SEAT_ABBREVIATIONS = Set.of("ceo", "md", "gm");

    private static final List<String> BOARD_WORDS = List.of("Chairman", "Board", "President", "Chief");
    private static final List<String> EXECUTIVE_WORDS = List.of("Chief", "Head", "Director", "VP");
    /** Not "Lead": the vendor finds it inside "Leadership" and "Thought leader", a headline's favourite words. */
    private static final List<String> MANAGER_WORDS = List.of("Manager", "Head", "Senior", "Principal");
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
        List<String> functionWords = functionWordsOfTitle(roleTitle);
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

    /**
     * The words for an index that matches substrings, those hiding inside longer words left out; empty when
     * that would leave a list with nothing, since a search by seniority or function alone finds anyone.
     */
    public Optional<SourcingSpec> forSubstringMatching() {
        List<String> seniority = outsideLongerWords(seniorityWords);
        List<String> function = outsideLongerWords(functionWords);
        if (seniority.isEmpty() != seniorityWords.isEmpty() || function.isEmpty() != functionWords.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SourcingSpec(seniority, function, excludedWords, roleSummary));
    }

    private static List<String> outsideLongerWords(List<String> words) {
        return words.stream()
                .filter(word -> !HIDDEN_IN_LONGER_WORDS.contains(word.toLowerCase(Locale.ROOT)))
                .toList();
    }

    /** A role title's own distinctive words, levels and glue left out: none for "Managing Director". */
    public static List<String> functionWordsOfTitle(String roleTitle) {
        return spelledOut(tokensOf(roleTitle == null ? List.of() : List.of(roleTitle))).stream()
                .filter(word -> !LEVEL_WORDS.contains(word.toLowerCase(Locale.ROOT)))
                .limit(MAX_WORDS)
                .toList();
    }

    /** Whether a title is the general-management seat (CEO, Managing Director), every word a level. */
    public static boolean namesTheTopSeat(String roleTitle) {
        List<String> words = tokensOf(roleTitle == null ? List.of() : List.of(roleTitle));
        return !words.isEmpty() && words.stream()
                .map(word -> word.toLowerCase(Locale.ROOT))
                .allMatch(word -> LEVEL_WORDS.contains(word) || TOP_SEAT_ABBREVIATIONS.contains(word));
    }

    /** The function words that name a function rather than a level: "Director" in the list proves nothing. */
    public List<String> functionNamingWords() {
        return functionWords.stream()
                .filter(word -> !LEVEL_WORDS.contains(word.toLowerCase(Locale.ROOT)))
                .toList();
    }

    /** The function-naming words no other function's titles also carry. */
    public List<String> distinctiveFunctionWords() {
        return functionNamingWords().stream()
                .filter(word -> !isSharedAcrossFunctions(word))
                .toList();
    }

    private static boolean isSharedAcrossFunctions(String word) {
        String folded = word.toLowerCase(Locale.ROOT);
        return SHARED_FUNCTION_WORDS.contains(folded) || SHARED_FUNCTION_WORDS.contains(singularOf(folded));
    }

    private static String singularOf(String word) {
        if (word.endsWith("ses") || word.endsWith("xes")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.endsWith("s") && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    /** The words as a search sees them — order and case ignored — so two specs asking the same question share it. */
    public String wordKey() {
        return String.join("|", sortedLower(seniorityWords), sortedLower(functionWords),
                "-" + sortedLower(excludedWords));
    }

    private static String sortedLower(List<String> words) {
        return String.join(",", words.stream().map(word -> word.toLowerCase(Locale.ROOT)).sorted().toList());
    }

    private static List<String> cleaned(List<String> words) {
        return spelledOut(tokensOf(words)).stream().limit(MAX_WORDS).toList();
    }

    /**
     * The abbreviations {@code words} spell out — "IT" for "Technology" — for a reader that matches whole
     * words, where an abbreviation is not found inside longer ones.
     */
    public static List<String> abbreviationsOf(List<String> words) {
        Set<String> folded = new LinkedHashSet<>(words.stream().map(word -> word.toLowerCase(Locale.ROOT)).toList());
        return ABBREVIATION_SPELLINGS.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .anyMatch(spelling -> folded.contains(spelling.toLowerCase(Locale.ROOT))))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /** Each noise word replaced by its spellings, or dropped when it has none; repeats dropped. */
    private static List<String> spelledOut(List<String> words) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> kept = new ArrayList<>();
        for (String word : words) {
            String folded = word.toLowerCase(Locale.ROOT);
            List<String> spellings = NOISE_WORDS.contains(folded)
                    ? ABBREVIATION_SPELLINGS.getOrDefault(folded.toUpperCase(Locale.ROOT), List.of())
                    : List.of(word);
            spellings.stream().filter(spelling -> seen.add(spelling.toLowerCase(Locale.ROOT))).forEach(kept::add);
        }
        return kept;
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
