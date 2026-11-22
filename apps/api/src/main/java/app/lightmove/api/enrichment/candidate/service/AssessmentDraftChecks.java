package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.core.llm.constant.EvalCheckVerdict;
import app.lightmove.api.core.llm.service.EvalCheck;
import app.lightmove.api.core.text.service.ContactRedaction;
import app.lightmove.api.core.text.service.LooseTextMatch;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * The rules an assessment draft is held to on a synthetic case. Each reads the model's own answer (ids, scores,
 * evidence, report), before the drafter drops what it cannot use, so an invented id or a score out of range counts.
 *
 * <p>An answer that could not be read fails the first check and gives the others nothing to judge: one outage is
 * then one failure, not eight.
 */
final class AssessmentDraftChecks {

    static final int SELF_REPORT_CEILING = 7;
    /** At or above this a score is a strong one, which a label may say must rest on a named source. */
    static final int STRONG_SCORE = 7;

    private static final String MONTH = "(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*";
    private static final Pattern DAY = Pattern.compile("\\b(\\d{1,2}) (" + MONTH + ") (\\d{4})\\b");
    /**
     * A call as the prompt asks for it to be named, by its day and the time into it: "call 12 Oct 2026, 14:05 in",
     * and as the model also writes it, "interview on 12 Oct 2026 (14:05 in)".
     */
    private static final Pattern CALL_CITED = Pattern.compile(
            "(?i)(\\d{1,2} " + MONTH + " \\d{4})[^A-Za-z0-9]{0,4}(?:at )?((?:\\d+:)?\\d{1,2}:\\d{2}) in\\b");
    /** The profile by name, or the prompt's own words for a score read from the career: "Inferred from …". */
    private static final Pattern PROFILE_NAMED =
            Pattern.compile("(?i)\\b(?:profile|career|linkedin|inferred from)\\b");
    private static final Pattern NOTE_NAMED = Pattern.compile("(?i)\\bnotes?\\b");
    private static final Pattern DOCUMENT_NAMED = Pattern.compile(
            "(?i)\\b(?:document|cv|résumé|resume|cover letter|reference|certificate|assessment report)\\b");
    private static final Pattern STATES_ABSENCE =
            Pattern.compile("(?i)\\b(?:no|nothing|none|not enough|insufficient)\\b");
    private static final Pattern MONEY = Pattern.compile(
            "(?i)(?:\\b(?:USD|EUR|GBP|AED|SAR|QAR|KWD|BHD|OMR)|US\\$|\\$|€|£)\\s?(\\d+(?:[.,]\\d+)*)");
    private static final Pattern MAGNITUDE =
            Pattern.compile("(?i)\\b(\\d+(?:[.,]\\d+)*)\\s?(?:m|bn|million|billion)\\b");
    private static final Pattern PERCENT = Pattern.compile("(?i)\\b(\\d+(?:[.,]\\d+)*)\\s?(?:%|per cent|percent)");
    private static final Pattern YEAR = Pattern.compile("\\b((?:19|20)\\d{2})\\b");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");
    private static final Pattern QUOTED = Pattern.compile("[\"“]([^\"“”]{12,})[\"”]");
    private static final int QUOTE_WORDS = 4;
    private static final DateTimeFormatter WRITTEN_DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    static final List<EvalCheck> ALL = List.of(
            EvalCheck.of("competency-ids", "Every competency id is valid", AssessmentDraftChecks::idsAreTheBriefs),
            EvalCheck.of("scores-in-range", "Scores are whole numbers in range", AssessmentDraftChecks::scoresInRange),
            EvalCheck.of("evidence-names-source", "Evidence names its source", AssessmentDraftChecks::namesItsSource),
            EvalCheck.of("evidence-in-input", "Evidence is found in the input", AssessmentDraftChecks::foundInInput),
            EvalCheck.of("no-contact", "No email or phone in any answer", AssessmentDraftChecks::holdsNoContact),
            EvalCheck.of("no-team-evidence",
                    "Nothing taken from \"(your team)\" or an unidentified speaker",
                    AssessmentDraftChecks::takesNothingFromOthers),
            EvalCheck.of("score-in-band", "Score inside the labelled band", AssessmentDraftChecks::scoresInBand),
            EvalCheck.of("self-report-cap", "Self-report alone stays at 7 or below",
                    AssessmentDraftChecks::selfReportStaysCapped));

    private AssessmentDraftChecks() {
    }

    /** One entry of an answer's {@code scores}: the id as the drafter reads it, the score as answered, the evidence. */
    private record Scored(String id, JsonNode score, String evidence) {

        boolean hasScore() {
            return score != null && !score.isNull();
        }
    }

    private static EvalCheckVerdict idsAreTheBriefs(JsonNode input, JsonNode expected, JsonNode answer) {
        if (answer == null) {
            return EvalCheckVerdict.FAILED;
        }
        Set<String> briefIds = AssessmentCase.competenciesOf(input).keySet();
        Set<String> answered = new HashSet<>();
        for (Scored scored : scoresOf(answer)) {
            if (scored.id() == null || !briefIds.contains(scored.id()) || !answered.add(scored.id())) {
                return EvalCheckVerdict.FAILED;
            }
        }
        return passedIf(answered.equals(briefIds));
    }

    private static EvalCheckVerdict scoresInRange(JsonNode input, JsonNode expected, JsonNode answer) {
        if (answer == null) {
            return EvalCheckVerdict.NOT_APPLICABLE;
        }
        for (Scored scored : scoresOf(answer)) {
            if (scored.hasScore()) {
                JsonNode score = scored.score();
                if (!score.isIntegralNumber() || score.asInt() < 0 || score.asInt() > AssessmentExpectation.MAX_SCORE) {
                    return EvalCheckVerdict.FAILED;
                }
            } else if (scored.evidence() != null && !STATES_ABSENCE.matcher(scored.evidence()).find()) {
                // No score says nothing on file bears on the competency: evidence beside it is evidence of nothing.
                return EvalCheckVerdict.FAILED;
            }
        }
        return EvalCheckVerdict.PASSED;
    }

    private static EvalCheckVerdict namesItsSource(JsonNode input, JsonNode expected, JsonNode answer) {
        List<Scored> scored = scoresOf(answer).stream().filter(Scored::hasScore).toList();
        if (scored.isEmpty()) {
            return EvalCheckVerdict.NOT_APPLICABLE;
        }
        List<String> documentTitles = new ArrayList<>();
        for (JsonNode document : input.path(AssessmentCase.DOCUMENTS)) {
            documentTitles.add(LooseTextMatch.normalised(AssessmentCase.textOf(document, "title")));
        }
        Set<String> noteDays = new HashSet<>();
        for (JsonNode note : input.path(AssessmentCase.NOTES)) {
            noteDays.add(WRITTEN_DAY.format(AssessmentCase.dayOf(note)));
        }
        for (Scored entry : scored) {
            Set<String> named = sourcesNamedIn(entry.evidence(), documentTitles, noteDays);
            // A score explained by there being nothing to read has no source to name.
            if (named.isEmpty() && (entry.evidence() == null || !STATES_ABSENCE.matcher(entry.evidence()).find())) {
                return EvalCheckVerdict.FAILED;
            }
            JsonNode label = entry.id() == null ? null : AssessmentExpectation.labelOf(expected, entry.id());
            String restsOn = label == null ? null : AssessmentExpectation.restsOn(label);
            if (restsOn != null && entry.score().asInt() >= STRONG_SCORE && !named.contains(restsOn)) {
                return EvalCheckVerdict.FAILED;
            }
        }
        return EvalCheckVerdict.PASSED;
    }

    private static Set<String> sourcesNamedIn(String evidence, List<String> documentTitles, Set<String> noteDays) {
        Set<String> named = new HashSet<>();
        if (evidence == null) {
            return named;
        }
        if (PROFILE_NAMED.matcher(evidence).find()) {
            named.add("profile");
        }
        if (CALL_CITED.matcher(evidence).find()) {
            named.add("call");
        }
        // A note is named by its date, whatever it is called: a call note is "the call on 14 Oct 2026".
        Matcher day = DAY.matcher(evidence);
        boolean dated = false;
        while (day.find()) {
            dated = true;
            if (noteDays.contains(dayAsWritten(day))) {
                named.add("note");
            }
        }
        if (dated && NOTE_NAMED.matcher(evidence).find()) {
            named.add("note");
        }
        Set<String> words = new HashSet<>(List.of(LooseTextMatch.normalised(evidence).split(" ")));
        if (DOCUMENT_NAMED.matcher(evidence).find()
                || documentTitles.stream().anyMatch(title -> words.containsAll(wordsOfTitle(title)))) {
            named.add("document");
        }
        return named;
    }

    /** A title's words that carry it, so "the January 2025 Board report" names "Board report, January 2025". */
    private static List<String> wordsOfTitle(String normalisedTitle) {
        return List.of(normalisedTitle.split(" ")).stream().filter(word -> word.length() > 2).toList();
    }

    private static EvalCheckVerdict foundInInput(JsonNode input, JsonNode expected, JsonNode answer) {
        List<String> evidence = scoresOf(answer).stream().map(Scored::evidence).filter(text -> text != null).toList();
        if (evidence.isEmpty()) {
            return EvalCheckVerdict.NOT_APPLICABLE;
        }
        StringBuilder everything = new StringBuilder();
        collectText(input, everything);
        String haystack = LooseTextMatch.normalised(everything.toString());
        Set<String> numbers = new HashSet<>();
        Matcher number = NUMBER.matcher(everything);
        while (number.find()) {
            numbers.add(plain(number.group()));
        }
        Set<String> days = new HashSet<>();
        for (JsonNode note : input.path(AssessmentCase.NOTES)) {
            days.add(WRITTEN_DAY.format(AssessmentCase.dayOf(note)));
        }
        Map<String, Set<Long>> lineTimesByCallDay = new HashMap<>();
        for (JsonNode transcript : input.path(AssessmentCase.TRANSCRIPTS)) {
            Set<Long> times = lineTimesByCallDay.computeIfAbsent(
                    WRITTEN_DAY.format(AssessmentCase.dayOf(transcript)), day -> new HashSet<>());
            for (JsonNode line : transcript.path("lines")) {
                times.add(AssessmentCase.secondsOf(AssessmentCase.textOf(line, "at")));
            }
        }
        days.addAll(lineTimesByCallDay.keySet());
        for (String text : evidence) {
            if (!datesAreTheCases(text, days, haystack) || !callTimesAreLines(text, lineTimesByCallDay)
                    || !figuresAreStated(text, numbers) || !quotesAreSaid(text, haystack)) {
                return EvalCheckVerdict.FAILED;
            }
        }
        return EvalCheckVerdict.PASSED;
    }

    private static boolean datesAreTheCases(String evidence, Set<String> days, String haystack) {
        Matcher day = DAY.matcher(evidence);
        while (day.find()) {
            String written = dayAsWritten(day);
            if (!days.contains(written) && !haystack.contains(LooseTextMatch.normalised(day.group()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean callTimesAreLines(String evidence, Map<String, Set<Long>> lineTimesByCallDay) {
        Matcher cited = CALL_CITED.matcher(evidence);
        while (cited.find()) {
            Matcher day = DAY.matcher(cited.group(1));
            Set<Long> times = day.find() ? lineTimesByCallDay.get(dayAsWritten(day)) : null;
            if (times == null || !times.contains(AssessmentCase.secondsOf(cited.group(2)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean figuresAreStated(String evidence, Set<String> numbers) {
        for (Pattern figure : List.of(MONEY, MAGNITUDE, PERCENT, YEAR)) {
            Matcher stated = figure.matcher(evidence);
            while (stated.find()) {
                if (!numbers.contains(plain(stated.group(1)))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean quotesAreSaid(String evidence, String haystack) {
        Matcher quoted = QUOTED.matcher(evidence);
        while (quoted.find()) {
            String words = LooseTextMatch.normalised(quoted.group(1));
            if (words.split(" ").length >= QUOTE_WORDS && !haystack.contains(words)) {
                return false;
            }
        }
        return true;
    }

    private static EvalCheckVerdict holdsNoContact(JsonNode input, JsonNode expected, JsonNode answer) {
        if (answer == null) {
            return EvalCheckVerdict.NOT_APPLICABLE;
        }
        List<String> planted = AssessmentExpectation.textsOf(expected, AssessmentExpectation.NEVER_IN_ANSWER).stream()
                .map(AssessmentDraftChecks::squeezed).toList();
        for (String text : textsOf(answer)) {
            String squeezed = squeezed(text);
            if (!ContactRedaction.redact(text).equals(text) || planted.stream().anyMatch(squeezed::contains)) {
                return EvalCheckVerdict.FAILED;
            }
        }
        return EvalCheckVerdict.PASSED;
    }

    private static EvalCheckVerdict takesNothingFromOthers(JsonNode input, JsonNode expected, JsonNode answer) {
        List<String> notEvidence = AssessmentExpectation.textsOf(expected, AssessmentExpectation.NOT_EVIDENCE).stream()
                .map(LooseTextMatch::normalised).toList();
        Set<String> unusableDays = new HashSet<>();
        for (String day : AssessmentExpectation.textsOf(expected, AssessmentExpectation.UNUSABLE_CALLS)) {
            unusableDays.add(WRITTEN_DAY.format(LocalDate.parse(day)));
        }
        if (answer == null || (notEvidence.isEmpty() && unusableDays.isEmpty())) {
            return EvalCheckVerdict.NOT_APPLICABLE;
        }
        for (String text : textsOf(answer)) {
            String normalised = LooseTextMatch.normalised(text);
            if (notEvidence.stream().anyMatch(normalised::contains)) {
                return EvalCheckVerdict.FAILED;
            }
            Matcher cited = CALL_CITED.matcher(text);
            while (cited.find()) {
                Matcher day = DAY.matcher(cited.group(1));
                if (day.find() && unusableDays.contains(dayAsWritten(day))) {
                    return EvalCheckVerdict.FAILED;
                }
            }
        }
        return EvalCheckVerdict.PASSED;
    }

    private static EvalCheckVerdict scoresInBand(JsonNode input, JsonNode expected, JsonNode answer) {
        if (answer == null) {
            return EvalCheckVerdict.NOT_APPLICABLE;
        }
        Map<String, Scored> byId = new HashMap<>();
        scoresOf(answer).forEach(scored -> byId.putIfAbsent(scored.id(), scored));
        for (Map.Entry<String, JsonNode> label : expected.path(AssessmentExpectation.COMPETENCIES).properties()) {
            Scored scored = byId.get(label.getKey());
            boolean hasScore = scored != null && scored.hasScore() && scored.score().isIntegralNumber();
            if (AssessmentExpectation.expectsNoScore(label.getValue())) {
                if (hasScore) {
                    return EvalCheckVerdict.FAILED;
                }
            } else if (!hasScore || scored.score().asInt() < AssessmentExpectation.lowestOf(label.getValue())
                    || scored.score().asInt() > AssessmentExpectation.highestOf(label.getValue())) {
                return EvalCheckVerdict.FAILED;
            }
        }
        return EvalCheckVerdict.PASSED;
    }

    private static EvalCheckVerdict selfReportStaysCapped(JsonNode input, JsonNode expected, JsonNode answer) {
        Set<String> selfReported = new HashSet<>();
        for (Map.Entry<String, JsonNode> label : expected.path(AssessmentExpectation.COMPETENCIES).properties()) {
            if (AssessmentExpectation.isSelfReportOnly(label.getValue())) {
                selfReported.add(label.getKey());
            }
        }
        if (answer == null || selfReported.isEmpty()) {
            return EvalCheckVerdict.NOT_APPLICABLE;
        }
        for (Scored scored : scoresOf(answer)) {
            if (selfReported.contains(scored.id()) && scored.hasScore()
                    && scored.score().asInt() > SELF_REPORT_CEILING) {
                return EvalCheckVerdict.FAILED;
            }
        }
        return EvalCheckVerdict.PASSED;
    }

    private static List<Scored> scoresOf(JsonNode answer) {
        List<Scored> scores = new ArrayList<>();
        if (answer != null) {
            for (JsonNode entry : answer.path("scores")) {
                String id = AssessmentCase.textOf(entry, "id");
                scores.add(new Scored(id == null ? null : id.toUpperCase(Locale.ROOT), entry.get("score"),
                        AssessmentCase.textOf(entry, "evidence")));
            }
        }
        return scores;
    }

    /** Every sentence of an answer a person would read: each competency's evidence and the report's sections. */
    private static List<String> textsOf(JsonNode answer) {
        List<String> texts = new ArrayList<>();
        scoresOf(answer).stream().map(Scored::evidence).filter(text -> text != null).forEach(texts::add);
        for (JsonNode section : answer.path("report")) {
            if (section.isString() && !section.asString().isBlank()) {
                texts.add(section.asString());
            }
        }
        return texts;
    }

    private static void collectText(JsonNode node, StringBuilder into) {
        if (node.isString()) {
            into.append(node.asString()).append('\n');
        } else if (node.isNumber()) {
            into.append(node.asString()).append('\n');
        } else {
            node.forEach(child -> collectText(child, into));
        }
    }

    /** {@code 14 October 2026} and {@code 14 Oct 2026} as the one way a case's own dates are written. */
    private static String dayAsWritten(Matcher day) {
        return Integer.parseInt(day.group(1)) + " " + day.group(2).substring(0, 3) + " " + day.group(3);
    }

    private static String plain(String number) {
        return number.replace(",", "");
    }

    private static String squeezed(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static EvalCheckVerdict passedIf(boolean held) {
        return held ? EvalCheckVerdict.PASSED : EvalCheckVerdict.FAILED;
    }
}
