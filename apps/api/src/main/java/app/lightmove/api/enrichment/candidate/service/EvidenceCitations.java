package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.core.text.service.LooseTextMatch;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** What a sentence of an assessment draft cites: the sources it names, the days it writes, the calls it times. */
final class EvidenceCitations {

    static final String PROFILE = "profile";
    static final String NOTE = "note";
    static final String CALL = "call";
    static final String DOCUMENT = "document";

    private static final String DAY_WORDS =
            "(\\d{1,2}) ((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*) (\\d{4})";
    private static final Pattern DAY = Pattern.compile("(?i)\\b" + DAY_WORDS + "\\b");
    /**
     * A call as the prompt asks for it to be named, by its day and the time into it: "call 12 Oct 2026, 14:05 in",
     * and as the model also writes it, "interview on 12 Oct 2026 (14:05 in)".
     */
    private static final Pattern CALL_CITED = Pattern.compile(
            "(?i)\\b" + DAY_WORDS + "[^A-Za-z0-9]{0,4}(?:at )?((?:\\d+:)?\\d{1,2}:\\d{2}) in\\b");
    /** The profile by name, or the prompt's own words for a score read from the career: "Inferred from …". */
    private static final Pattern PROFILE_NAMED =
            Pattern.compile("(?i)\\b(?:profile|career|linkedin|inferred from)\\b");
    private static final Pattern NOTE_NAMED = Pattern.compile("(?i)\\bnotes?\\b");
    private static final Pattern DOCUMENT_NAMED = Pattern.compile(
            "(?i)\\b(?:document|cv|résumé|resume|cover letter|reference|certificate|assessment report)\\b");
    /**
     * That the material holds nothing, never a bare "no": "Led the turnaround with no outside hires" once passed as
     * a score with nothing to cite.
     */
    private static final Pattern NOTHING_ON_FILE = Pattern.compile(
            "(?i)\\b(?:no|not enough|insufficient|little)\\s+(?:\\w+\\s+){0,2}"
                    + "(?:evidence|information|material|mention|record|indication)\\b"
                    + "|\\b(?:nothing|none)\\s+(?:\\w+\\s+){0,3}(?:on file|bears on)\\b"
                    + "|\\bnot (?:mentioned|evidenced|covered|addressed|discussed)\\b");
    private static final DateTimeFormatter WRITTEN_DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private EvidenceCitations() {
    }

    /** @param day the date as {@link #written} gives it; @param words the text's own spelling of it */
    record WrittenDay(String day, String words) {
    }

    record CitedCall(String day, long secondsIn) {
    }

    static String written(LocalDate day) {
        return WRITTEN_DAY.format(day);
    }

    static List<WrittenDay> daysIn(String text) {
        List<WrittenDay> days = new ArrayList<>();
        Matcher day = DAY.matcher(text);
        while (day.find()) {
            days.add(new WrittenDay(written(day.group(1), day.group(2), day.group(3)), day.group()));
        }
        return days;
    }

    static List<CitedCall> callsIn(String text) {
        List<CitedCall> calls = new ArrayList<>();
        Matcher cited = CALL_CITED.matcher(text);
        while (cited.find()) {
            calls.add(new CitedCall(written(cited.group(1), cited.group(2), cited.group(3)),
                    AssessmentCase.secondsOf(cited.group(4))));
        }
        return calls;
    }

    static boolean saysNothingIsOnFile(String evidence) {
        return evidence != null && NOTHING_ON_FILE.matcher(evidence).find();
    }

    /**
     * @param documentTitles each as {@link LooseTextMatch#normalised} writes it
     * @param noteDays       each as {@link #written} gives it
     */
    static Set<String> sourcesNamedIn(String evidence, List<String> documentTitles, Set<String> noteDays) {
        Set<String> named = new HashSet<>();
        if (evidence == null) {
            return named;
        }
        if (PROFILE_NAMED.matcher(evidence).find()) {
            named.add(PROFILE);
        }
        if (!callsIn(evidence).isEmpty()) {
            named.add(CALL);
        }
        // A note is named by its date, whatever it is called: a call note is "the call on 14 Oct 2026".
        List<WrittenDay> days = daysIn(evidence);
        if (days.stream().anyMatch(day -> noteDays.contains(day.day()))
                || (!days.isEmpty() && NOTE_NAMED.matcher(evidence).find())) {
            named.add(NOTE);
        }
        Set<String> words = new HashSet<>(List.of(LooseTextMatch.normalised(evidence).split(" ")));
        if (DOCUMENT_NAMED.matcher(evidence).find()
                || documentTitles.stream().anyMatch(title -> words.containsAll(wordsOfTitle(title)))) {
            named.add(DOCUMENT);
        }
        return named;
    }

    /** A title's words that carry it, so "the January 2025 Board report" names "Board report, January 2025". */
    private static List<String> wordsOfTitle(String normalisedTitle) {
        return List.of(normalisedTitle.split(" ")).stream().filter(word -> word.length() > 2).toList();
    }

    /** {@code 14 October 2026}, {@code 14 OCT 2026} and {@code 14 Oct 2026} as the one way a case's dates are written. */
    private static String written(String dayOfMonth, String month, String year) {
        return Integer.parseInt(dayOfMonth) + " " + month.substring(0, 1).toUpperCase(Locale.ROOT)
                + month.substring(1, 3).toLowerCase(Locale.ROOT) + " " + year;
    }
}
