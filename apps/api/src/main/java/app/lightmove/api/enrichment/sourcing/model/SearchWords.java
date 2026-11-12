package app.lightmove.api.enrichment.sourcing.model;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.enrichment.sourcing.constant.SearchLevel;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * What a search looks for in a person's current job title, matched as whole words: one word of an allowed
 * level and one function term, or a standalone title ("CIO"), and no excluded word. The function terms
 * cover the whole function, core terms first, and may be short phrases ("Real Estate"); the level words
 * come from {@link SearchLevel}s plus any the market's own language uses ("Direktörü"). Suggestions are
 * further function terms the search does not use until someone adds them.
 *
 * <p>Cleans itself, whoever wrote it — the model, a person, or a run recorded before the shape changed
 * ({@code seniorityWords} and {@code functionWords} then, read back as local level words and function terms).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchWords(List<SearchLevel> levels,
                          @JsonAlias("functionWords") List<String> functionTerms,
                          List<String> standaloneTitles,
                          @JsonAlias("seniorityWords") List<String> localLevelWords,
                          List<String> excludedWords,
                          List<String> suggestions,
                          String roleSummary) {

    public static final int MAX_FUNCTION_TERMS = 12;
    public static final int MAX_STANDALONE_TITLES = 6;
    public static final int MAX_LOCAL_LEVEL_WORDS = 6;
    public static final int MAX_EXCLUDED_WORDS = 4;
    public static final int MAX_SUGGESTIONS = 8;

    /** The leading function terms a search tries first: the function itself, before its sub-functions. */
    public static final int CORE_TERMS = 4;

    private static final int MAX_WORDS_IN_A_TERM = 3;

    private static final Pattern NOT_A_TERM_CHARACTER = Pattern.compile("[^\\p{L}\\p{N}&.' -]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    /** Words a Boolean title query reads as operators: never a term alone, and never in capitals inside one. */
    private static final Set<String> OPERATORS = Set.of("AND", "OR", "NOT");

    private static final Set<String> GLUE_WORDS = Set.of("of", "the", "and", "for", "to", "in", "at", "a", "an",
            "&");

    /** Words a title carries that say where it sits rather than what it does. */
    private static final Set<String> LEVEL_WORDS = Set.of("head", "chief", "director", "vp", "vice", "president",
            "officer", "manager", "senior", "group", "regional", "global", "lead", "executive", "general",
            "managing", "deputy", "assistant", "division", "department", "unit", "business", "mena", "gcc", "uae",
            "ksa", "svp", "evp", "avp", "principal", "specialist", "supervisor");

    /** The general-management seat's abbreviations, which a role title may be alone. */
    private static final Set<String> TOP_SEAT_ABBREVIATIONS = Set.of("ceo", "md", "gm");

    /** A function abbreviation and the terms a title spells it out in, both ways round. */
    private static final Map<String, List<String>> FUNCTION_SPELLINGS = Map.of(
            "IT", List.of("Information Technology", "Technology"),
            "HR", List.of("Human Resources", "Human Resource", "People"),
            "PR", List.of("Public Relations", "Communications"));

    /** A C-suite title's own abbreviation and the function terms it stands for — more than one function's for some. */
    private static final Map<String, List<String>> CHIEF_FUNCTIONS = Map.ofEntries(
            Map.entry("CFO", List.of("Finance", "Financial")),
            Map.entry("CTO", List.of("Technology", "IT")),
            Map.entry("CIO", List.of("Information", "IT", "Technology")),
            Map.entry("CDO", List.of("Digital", "Data")),
            Map.entry("CMO", List.of("Marketing", "Brand")),
            Map.entry("CHRO", List.of("HR", "Human Resources", "People")),
            Map.entry("CPO", List.of("People", "Product", "Procurement")),
            Map.entry("COO", List.of("Operations", "Operating")),
            Map.entry("CCO", List.of("Commercial")),
            Map.entry("CISO", List.of("Security", "Cybersecurity")),
            Map.entry("CRO", List.of("Risk", "Revenue")));

    /** Abbreviations naming several functions, so a spelled-out title's own word is the only term it gives. */
    private static final Set<String> AMBIGUOUS_CHIEFS = Set.of("CDO", "CPO", "CRO");

    private static final List<String> TOP_SEAT_TITLES = List.of("CEO", "Chief Executive", "Managing Director",
            "General Manager");
    private static final List<String> SUPPORT_WORDS = List.of("Assistant", "Secretary", "Coordinator", "Intern");

    public SearchWords {
        levels = levels == null ? List.of() : levels.stream().filter(Objects::nonNull).distinct().sorted().toList();
        functionTerms = terms(functionTerms, MAX_FUNCTION_TERMS).stream()
                .filter(term -> !isLevelOnly(term))
                .toList();
        standaloneTitles = terms(standaloneTitles, MAX_STANDALONE_TITLES);
        localLevelWords = terms(localLevelWords, MAX_LOCAL_LEVEL_WORDS);
        List<String> searched = Stream.of(levelWordsOf(levels, localLevelWords), functionTerms, standaloneTitles)
                .flatMap(List::stream)
                .map(SearchWords::folded)
                .toList();
        excludedWords = terms(excludedWords, Integer.MAX_VALUE).stream()
                .filter(word -> !searched.contains(folded(word)))
                .limit(MAX_EXCLUDED_WORDS)
                .toList();
        List<String> used = Stream.concat(functionTerms.stream(), standaloneTitles.stream())
                .map(SearchWords::folded)
                .toList();
        suggestions = terms(suggestions, Integer.MAX_VALUE).stream()
                .filter(term -> !used.contains(folded(term)) && !isLevelOnly(term))
                .limit(MAX_SUGGESTIONS)
                .toList();
        roleSummary = roleSummary == null || roleSummary.isBlank() ? null : roleSummary.strip();
    }

    /** A function term, a standalone title, or both: a search with neither would find every manager. */
    @JsonIgnore
    public boolean isSearchable() {
        return !functionTerms.isEmpty() || !standaloneTitles.isEmpty();
    }

    /**
     * Every word of the function terms and standalone titles but the level and glue words: what a match over a
     * whole profile can key on, where "Managing Director" would find every director.
     */
    public List<String> roleWords() {
        Set<String> seen = new LinkedHashSet<>();
        return Stream.concat(functionTerms.stream(), standaloneTitles.stream())
                .flatMap(term -> Arrays.stream(term.split(" ")))
                .filter(word -> !LEVEL_WORDS.contains(folded(word)) && !GLUE_WORDS.contains(folded(word)))
                .filter(word -> seen.add(folded(word)))
                .toList();
    }

    /** Every word that marks an allowed level, the bands' own and the market's. */
    public List<String> levelWords() {
        return levelWordsOf(levels, localLevelWords);
    }

    public List<String> coreTerms() {
        return functionTerms.stream().limit(CORE_TERMS).toList();
    }

    /** The same words held to the levels at or above {@code lowest} and to the core terms: a search's first tier. */
    public SearchWords narrowedTo(SearchLevel lowest) {
        return new SearchWords(levels.stream().filter(level -> level.isAtLeast(lowest)).toList(), coreTerms(),
                standaloneTitles, localLevelWords, excludedWords, List.of(), roleSummary);
    }

    /** The same words at one band alone; titles that stand alone count only at the top band they belong to. */
    public SearchWords atLevel(SearchLevel level) {
        return new SearchWords(List.of(level), functionTerms,
                level == SearchLevel.C_LEVEL ? standaloneTitles : List.of(), List.of(), excludedWords, List.of(),
                roleSummary);
    }

    /** The same levels and exclusions with one function term in place of all of them: what a suggestion would reach. */
    public SearchWords withTermAlone(String term) {
        return new SearchWords(levels, List.of(term), List.of(), localLevelWords, excludedWords, List.of(), roleSummary);
    }

    /** The lowest band allowed, or the seat's own when none is. */
    public SearchLevel lowestLevel(Seniority seat) {
        return levels.isEmpty() ? SearchLevel.ofSeat(seat) : levels.getLast();
    }

    /** The words as a search sees them — order and case aside — so two asking the same question share a key. */
    public String key() {
        return String.join("|", String.join(",", levels.stream().map(Enum::name).toList()),
                sortedFolded(functionTerms), sortedFolded(standaloneTitles), sortedFolded(localLevelWords),
                "-" + sortedFolded(excludedWords));
    }

    /**
     * What a search looks for when the model could not say: the seniority's bands, and the role title's
     * own terms — spelt out where the title is an abbreviation, the general-management seat's titles where
     * it names no function at all.
     */
    public static SearchWords defaultFor(String roleTitle, Seniority seniority) {
        String summary = roleTitle == null || roleTitle.isBlank() ? null : roleTitle.strip();
        if (namesTheTopSeat(roleTitle)) {
            return new SearchWords(List.of(SearchLevel.C_LEVEL), List.of(), TOP_SEAT_TITLES, List.of(),
                    List.of("Assistant", "Vice", "Secretary"), List.of(), summary);
        }
        List<String> standalone = new ArrayList<>();
        List<String> terms = new ArrayList<>();
        for (String term : functionTermsOfTitle(roleTitle)) {
            String upper = term.toUpperCase(Locale.ROOT);
            if (CHIEF_FUNCTIONS.containsKey(upper)) {
                standalone.add(upper);
                terms.addAll(CHIEF_FUNCTIONS.get(upper));
            } else {
                terms.add(term);
            }
        }
        chiefAbbreviationOf(roleTitle).ifPresent(abbreviation -> {
            standalone.add(abbreviation);
            if (!AMBIGUOUS_CHIEFS.contains(abbreviation)) {
                terms.addAll(CHIEF_FUNCTIONS.getOrDefault(abbreviation, List.of()));
            }
        });
        terms.addAll(spellingsOf(terms));
        return new SearchWords(SearchLevel.rangeFor(seniority), terms, standalone, List.of(), SUPPORT_WORDS,
                List.of(), summary);
    }

    /** Whether a title is the general-management seat (CEO, Managing Director), every word a level. */
    public static boolean namesTheTopSeat(String roleTitle) {
        List<String> words = wordsOf(roleTitle);
        return !words.isEmpty() && words.stream()
                .map(SearchWords::folded)
                .allMatch(word -> LEVEL_WORDS.contains(word) || TOP_SEAT_ABBREVIATIONS.contains(word)
                        || GLUE_WORDS.contains(word));
    }

    /**
     * A role title's own terms that name a function: each run of words between its level and glue words, so
     * "General Manager, Real Estate" gives "Real Estate" and "Managing Director" gives none.
     */
    public static List<String> functionTermsOfTitle(String roleTitle) {
        List<String> terms = new ArrayList<>();
        List<String> run = new ArrayList<>();
        for (String word : wordsOf(roleTitle)) {
            if (LEVEL_WORDS.contains(folded(word)) || GLUE_WORDS.contains(folded(word))) {
                addRun(run, terms);
            } else {
                run.add(word);
            }
        }
        addRun(run, terms);
        return terms.stream().filter(term -> term.length() > 1).distinct().toList();
    }

    private static void addRun(List<String> run, List<String> terms) {
        if (!run.isEmpty()) {
            terms.add(String.join(" ", run));
            run.clear();
        }
    }

    /** "CFO" for "Chief Financial Officer": a chief title naming one function word has a standalone abbreviation. */
    private static Optional<String> chiefAbbreviationOf(String roleTitle) {
        List<String> words = wordsOf(roleTitle);
        if (words.size() != 3 || !folded(words.getFirst()).equals("chief") || !folded(words.getLast()).equals("officer")) {
            return Optional.empty();
        }
        return Optional.of(("C" + words.get(1).charAt(0) + "O").toUpperCase(Locale.ROOT));
    }

    /** The other spelling of each abbreviated function among {@code terms}: "HR" for "Human Resources", and back. */
    private static List<String> spellingsOf(List<String> terms) {
        Set<String> present = new LinkedHashSet<>(terms.stream().map(SearchWords::folded).toList());
        List<String> spellings = new ArrayList<>();
        FUNCTION_SPELLINGS.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(function -> {
            boolean named = present.contains(folded(function.getKey()))
                    || function.getValue().stream().anyMatch(term -> present.contains(folded(term)));
            if (named) {
                spellings.add(function.getKey());
                spellings.addAll(function.getValue());
            }
        });
        return spellings;
    }

    private static List<String> levelWordsOf(List<SearchLevel> levels, List<String> localLevelWords) {
        return Stream.concat(levels.stream().flatMap(level -> level.words().stream()), localLevelWords.stream())
                .toList();
    }

    private static boolean isLevelOnly(String term) {
        return Arrays.stream(term.split(" ")).map(SearchWords::folded).allMatch(LEVEL_WORDS::contains);
    }

    /**
     * Each term trimmed to letters, digits and the joiners titles use ("M&A", "E-commerce", "L'Oréal"),
     * spaces collapsed, at most three words, never an operator; repeats dropped whatever their case.
     */
    private static List<String> terms(List<String> raw, int max) {
        if (raw == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> kept = new ArrayList<>();
        for (String term : raw) {
            if (term == null) {
                continue;
            }
            String cleaned = SPACES.matcher(NOT_A_TERM_CHARACTER.matcher(term).replaceAll(" ")).replaceAll(" ")
                    .strip();
            if (cleaned.isEmpty() || cleaned.split(" ").length > MAX_WORDS_IN_A_TERM || isOperator(cleaned)
                    || !seen.add(folded(cleaned))) {
                continue;
            }
            kept.add(cleaned);
            if (kept.size() == max) {
                break;
            }
        }
        return List.copyOf(kept);
    }

    private static boolean isOperator(String term) {
        return OPERATORS.contains(term.toUpperCase(Locale.ROOT))
                || Arrays.stream(term.split(" ")).anyMatch(OPERATORS::contains);
    }

    private static List<String> wordsOf(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("[^\\p{L}\\p{N}&]+")).filter(word -> !word.isEmpty()).toList();
    }

    private static String sortedFolded(List<String> words) {
        return String.join(",", words.stream().map(SearchWords::folded).sorted().toList());
    }

    private static String folded(String word) {
        return word.toLowerCase(Locale.ROOT);
    }
}
