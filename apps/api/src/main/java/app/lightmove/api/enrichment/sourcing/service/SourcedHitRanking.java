package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataExperience;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataPosition;
import app.lightmove.api.enrichment.sourcing.constant.TitleLevel;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Orders a company's hits so the picks are the best fits rather than the first the vendor happened to
 * return — the Search API sends no relevance order for a filter. The vendor matched the words anywhere in
 * the headline, so the current role's own title, less the employer's name, is read here: first whether it
 * carries a function word (a headline quoting a past seat, "ex-CFO", does not), then how near its level
 * sits to the brief's seat,
 * the higher of two equally near, and the vendor's order last. No model call, and nothing more bought.
 */
final class SourcedHitRanking {

    private static final Pattern NOT_A_LETTER = Pattern.compile("[^\\p{L}\\p{N}]+");

    /** Where a title stops naming the job and starts naming the employer: "CFO @ X", "CFO at X", "CFO | X". */
    private static final Pattern AT_EMPLOYER = Pattern.compile("\\s+(?:@|at|\\|)\\s+", Pattern.CASE_INSENSITIVE);

    /** "Head of Legal - DP World Trade Finance - DIFC", "Head of Sales, DP World Financial Services". */
    private static final Pattern TITLE_SEGMENT = Pattern.compile("\\s*(?:,|\\s[-–—]\\s)\\s*");

    /** A seat served rather than held: "Business Advisor to the Chief Digital Officer" reads as an advisor. */
    private static final Pattern SERVING = Pattern.compile("\\s+to\\s+(?:the\\s+)?", Pattern.CASE_INSENSITIVE);

    /**
     * The function abbreviations {@code SourcingSpec} spells out, since Bright Data finds "IT" inside
     * "Kuwait": read here, and asked of ContactOut, as whole words beside the spellings that imply them.
     */
    static final Map<String, Set<String>> ABBREVIATION_SPELLINGS = Map.of(
            "IT", Set.of("technology", "cio", "cto", "information"),
            "HR", Set.of("human", "people", "chro"),
            "PR", Set.of("communications"));

    private static final Set<String> TOP_WORDS = Set.of("chief", "ceo", "cfo", "coo", "cto", "cio", "chro", "cmo",
            "cso", "cco", "cdo", "cpo", "cro", "cao", "ciso", "chairman", "chairwoman", "chairperson");
    private static final Set<String> SENIOR_VICE_PRESIDENT_WORDS = Set.of("evp", "svp");
    private static final Set<String> HEAD_WORDS = Set.of("vp", "avp", "head", "director");
    private static final Set<String> MANAGER_WORDS = Set.of("manager", "lead", "principal");
    private static final Set<String> STEP_DOWN_WORDS = Set.of("deputy", "assistant", "associate");

    private SourcedHitRanking() {
    }

    static List<BrightDataPerson> ranked(List<BrightDataPerson> hits, SourcingSpec spec, Seniority seat) {
        TitleLevel wanted = TitleLevel.ofSeat(seat);
        List<RankedHit> scored = IntStream.range(0, hits.size())
                .mapToObj(index -> rankedHit(hits.get(index), index, spec, wanted))
                .toList();
        return scored.stream()
                .sorted(Comparator.comparing(RankedHit::functionFits).reversed()
                        .thenComparingInt(RankedHit::distance)
                        .thenComparing(RankedHit::level, Comparator.reverseOrder())
                        .thenComparingInt(RankedHit::vendorOrder))
                .map(RankedHit::person)
                .toList();
    }

    private static RankedHit rankedHit(BrightDataPerson person, int vendorOrder, SourcingSpec spec,
                                       TitleLevel wanted) {
        String title = roleOnly(currentRoleTitle(person), employerNameOf(person));
        TitleLevel level = levelOf(title);
        boolean functionFits = containsAny(title, spec.functionWords())
                || hasAbbreviationOf(title, spec.functionWords());
        return new RankedHit(person, functionFits, level.distanceTo(wanted), level, vendorOrder);
    }

    /**
     * The title of the seat held now — the first experience entry still open, or its first open role
     * when the company groups several — falling back to the headline when the record has none, or has
     * it masked.
     */
    static String currentRoleTitle(BrightDataPerson person) {
        List<BrightDataExperience> experience = person.experience() == null ? List.of() : person.experience();
        for (BrightDataExperience entry : experience) {
            if (entry == null || !isOpen(entry.endDate())) {
                continue;
            }
            if (entry.positions() != null) {
                for (BrightDataPosition role : entry.positions()) {
                    if (role != null && isOpen(role.endDate()) && isReadable(role.title())) {
                        return role.title();
                    }
                }
            }
            if (isReadable(entry.title())) {
                return entry.title();
            }
        }
        return person.position() == null ? "" : person.position();
    }

    /**
     * The job alone: the text before any "@ / at / |", less every segment naming the employer — a
     * business unit such as "DP World Trade Finance" made a head of legal read as a finance seat. The
     * title itself when that would leave nothing.
     */
    static String roleOnly(String title, String employerName) {
        String role = AT_EMPLOYER.split(title, 2)[0];
        String employer = employerName == null || employerName.isBlank() ? null
                : employerName.strip().toLowerCase(Locale.ROOT);
        String kept = Arrays.stream(TITLE_SEGMENT.split(role))
                .filter(segment -> !segment.isBlank())
                .filter(segment -> employer == null || !segment.toLowerCase(Locale.ROOT).contains(employer))
                .collect(Collectors.joining(", "));
        return kept.isBlank() ? title : kept;
    }

    private static String employerNameOf(BrightDataPerson person) {
        if (person.currentCompanyName() != null && !person.currentCompanyName().isBlank()) {
            return person.currentCompanyName();
        }
        return person.currentCompany() == null ? null : person.currentCompany().name();
    }

    static TitleLevel levelOf(String title) {
        if (!title.toLowerCase(Locale.ROOT).contains("chief of staff")) {
            title = SERVING.split(title, 2)[0];
        }
        List<String> words = List.of(NOT_A_LETTER.split(title.toLowerCase(Locale.ROOT)));
        String spaced = " " + String.join(" ", words) + " ";
        TitleLevel level;
        if (spaced.contains(" chief of staff ")) {
            level = TitleLevel.HEAD;
        } else if (words.stream().anyMatch(TOP_WORDS::contains) || spaced.contains(" managing director ")
                || spaced.contains(" general manager ")
                || (words.contains("president") && !words.contains("vice"))) {
            level = TitleLevel.TOP;
        } else if (words.stream().anyMatch(SENIOR_VICE_PRESIDENT_WORDS::contains)
                || spaced.contains(" executive vice president ") || spaced.contains(" senior vice president ")) {
            level = TitleLevel.SENIOR_VICE_PRESIDENT;
        } else if (words.stream().anyMatch(HEAD_WORDS::contains) || spaced.contains(" vice president ")) {
            level = TitleLevel.HEAD;
        } else if (words.stream().anyMatch(MANAGER_WORDS::contains)) {
            level = TitleLevel.MANAGER;
        } else {
            level = TitleLevel.NONE;
        }
        boolean steppedDown = words.stream().anyMatch(STEP_DOWN_WORDS::contains);
        return steppedDown && level != TitleLevel.NONE ? TitleLevel.values()[level.ordinal() - 1] : level;
    }

    /** "Head of IT" for technology words: the abbreviation as a whole word, never inside another. */
    private static boolean hasAbbreviationOf(String title, List<String> functionWords) {
        List<String> words = List.of(NOT_A_LETTER.split(title.toLowerCase(Locale.ROOT)));
        return ABBREVIATION_SPELLINGS.entrySet().stream()
                .filter(entry -> functionWords.stream()
                        .anyMatch(word -> entry.getValue().contains(word.toLowerCase(Locale.ROOT))))
                .anyMatch(entry -> words.contains(entry.getKey().toLowerCase(Locale.ROOT)));
    }

    /** The vendor's own rule — a case-insensitive substring — so a title the search matched still matches here. */
    private static boolean containsAny(String title, List<String> words) {
        String folded = title.toLowerCase(Locale.ROOT);
        return words.stream().anyMatch(word -> folded.contains(word.toLowerCase(Locale.ROOT)));
    }

    private static boolean isOpen(String endDate) {
        return endDate == null || endDate.isBlank() || endDate.strip().equalsIgnoreCase("present");
    }

    private static boolean isReadable(String title) {
        return title != null && !title.isBlank() && !title.contains("*");
    }

    private record RankedHit(BrightDataPerson person, boolean functionFits, int distance, TitleLevel level,
                             int vendorOrder) {}
}
