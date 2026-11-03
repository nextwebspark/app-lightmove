package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataExperience;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataPosition;
import app.lightmove.api.enrichment.sourcing.constant.TitleLevel;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The job a person holds now, read from their record as every index spells it — the open role, less the
 * employer's name — and the level its title reads at.
 */
final class CurrentRole {

    /** Where a title stops naming the job and starts naming the employer: "CFO @ X", "CFO at X", "CFO | X". */
    private static final Pattern AT_EMPLOYER = Pattern.compile("\\s+(?:@|at|\\|)\\s+", Pattern.CASE_INSENSITIVE);

    /** "Head of Legal - DP World Trade Finance - DIFC", "Head of Sales, DP World Financial Services". */
    private static final Pattern TITLE_SEGMENT = Pattern.compile("\\s*(?:,|\\s[-–—]\\s)\\s*");

    /** A seat served rather than held: "Business Advisor to the Chief Digital Officer" reads as an advisor. */
    private static final Pattern SERVING = Pattern.compile("\\s+to\\s+(?:the\\s+)?", Pattern.CASE_INSENSITIVE);

    private static final Set<String> TOP_WORDS = Set.of("chief", "ceo", "cfo", "coo", "cto", "cio", "chro", "cmo",
            "cso", "cco", "cdo", "cpo", "cro", "cao", "ciso", "chairman", "chairwoman", "chairperson");
    private static final Set<String> SENIOR_VICE_PRESIDENT_WORDS = Set.of("evp", "svp");
    private static final Set<String> HEAD_WORDS = Set.of("vp", "avp", "head", "director", "treasurer");
    private static final Set<String> MANAGER_WORDS = Set.of("manager", "lead", "principal");
    private static final Set<String> STEP_DOWN_WORDS = Set.of("deputy", "assistant", "associate");

    private CurrentRole() {
    }

    /** The job held now, less the employer's name: what a person is judged and scored by. */
    static String of(BrightDataPerson person) {
        return roleOnly(titleOf(person), employerNameOf(person));
    }

    /**
     * The title of the seat held now — the first experience entry still open, or its first open role
     * when the company groups several — falling back to the headline when the record has none, or has
     * it masked.
     */
    static String titleOf(BrightDataPerson person) {
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
        List<String> words = TitleWords.wordsOf(title);
        String spaced = " " + String.join(" ", words) + " ";
        TitleLevel level;
        if (spaced.contains(" chief of staff ") || spaced.contains(" general counsel ")) {
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
        return steppedDown ? level.oneDown() : level;
    }

    private static boolean isOpen(String endDate) {
        return endDate == null || endDate.isBlank() || endDate.strip().equalsIgnoreCase("present");
    }

    private static boolean isReadable(String title) {
        return title != null && !title.isBlank() && !title.contains("*");
    }

}
