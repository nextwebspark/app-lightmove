package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.CandidateScorecard;
import app.lightmove.api.core.llm.constant.EvalCheckVerdict;
import app.lightmove.api.core.llm.service.EvalCheck;
import app.lightmove.api.core.text.service.ContactRedaction;
import app.lightmove.api.core.text.service.LooseTextMatch;
import java.time.LocalDate;
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

    private static final Pattern MONEY = Pattern.compile(
            "(?i)(?:\\b(?:USD|EUR|GBP|AED|SAR|QAR|KWD|BHD|OMR)|US\\$|\\$|€|£)\\s?(\\d+(?:[.,]\\d+)*)");
    private static final Pattern MAGNITUDE =
            Pattern.compile("(?i)\\b(\\d+(?:[.,]\\d+)*)\\s?(?:m|bn|million|billion)\\b");
    private static final Pattern PERCENT = Pattern.compile("(?i)\\b(\\d+(?:[.,]\\d+)*)\\s?(?:%|per cent|percent)");
    private static final Pattern YEAR = Pattern.compile("\\b((?:19|20)\\d{2})\\b");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");
    private static final Pattern QUOTED = Pattern.compile("[\"“]([^\"“”]{12,})[\"”]");
    private static final int QUOTE_WORDS = 4;

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

        // The answer is the model's own JSON, so a score may be "N/A" or an object, on which asInt() throws.
        boolean hasWholeScore() {
            return hasScore() && score.isIntegralNumber() && score.canConvertToInt();
        }

        int wholeScore() {
            return score.asInt();
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
                if (!scored.hasWholeScore() || scored.wholeScore() < CandidateScorecard.MIN_SCORE
                        || scored.wholeScore() > CandidateScorecard.MAX_SCORE) {
                    return EvalCheckVerdict.FAILED;
                }
            } else if (scored.evidence() != null && !EvidenceCitations.saysNothingIsOnFile(scored.evidence())) {
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
        Set<String> noteDays = daysOf(input.path(AssessmentCase.NOTES));
        for (Scored entry : scored) {
            Set<String> named = EvidenceCitations.sourcesNamedIn(entry.evidence(), documentTitles, noteDays);
            // Only a low score can be explained by there being nothing to read, which leaves no source to name.
            boolean nothingToName = entry.hasWholeScore() && entry.wholeScore() < STRONG_SCORE
                    && EvidenceCitations.saysNothingIsOnFile(entry.evidence());
            if (named.isEmpty() && !nothingToName) {
                return EvalCheckVerdict.FAILED;
            }
            JsonNode label = entry.id() == null ? null : AssessmentExpectation.labelOf(expected, entry.id());
            String restsOn = label == null ? null : AssessmentExpectation.restsOn(label);
            if (restsOn != null && entry.hasWholeScore() && entry.wholeScore() >= STRONG_SCORE
                    && !named.contains(restsOn)) {
                return EvalCheckVerdict.FAILED;
            }
        }
        return EvalCheckVerdict.PASSED;
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
        Set<String> days = daysOf(input.path(AssessmentCase.NOTES));
        Map<String, Set<Long>> lineTimesByCallDay = new HashMap<>();
        for (JsonNode transcript : input.path(AssessmentCase.TRANSCRIPTS)) {
            Set<Long> times = lineTimesByCallDay.computeIfAbsent(
                    EvidenceCitations.written(AssessmentCase.dayOf(transcript)), day -> new HashSet<>());
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
        return EvidenceCitations.daysIn(evidence).stream().allMatch(written -> days.contains(written.day())
                || haystack.contains(LooseTextMatch.normalised(written.words())));
    }

    private static boolean callTimesAreLines(String evidence, Map<String, Set<Long>> lineTimesByCallDay) {
        return EvidenceCitations.callsIn(evidence).stream().allMatch(call ->
                lineTimesByCallDay.getOrDefault(call.day(), Set.of()).contains(call.secondsIn()));
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
        List<String> planted = AssessmentCase.textsOf(expected, AssessmentExpectation.NEVER_IN_ANSWER).stream()
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
        List<String> notEvidence = AssessmentCase.textsOf(expected, AssessmentExpectation.NOT_EVIDENCE).stream()
                .map(LooseTextMatch::normalised).toList();
        Set<String> unusableDays = new HashSet<>();
        for (String day : AssessmentCase.textsOf(expected, AssessmentExpectation.UNUSABLE_CALLS)) {
            unusableDays.add(EvidenceCitations.written(LocalDate.parse(day)));
        }
        if (answer == null || (notEvidence.isEmpty() && unusableDays.isEmpty())) {
            return EvalCheckVerdict.NOT_APPLICABLE;
        }
        for (String text : textsOf(answer)) {
            String normalised = LooseTextMatch.normalised(text);
            if (notEvidence.stream().anyMatch(normalised::contains)) {
                return EvalCheckVerdict.FAILED;
            }
            if (EvidenceCitations.callsIn(text).stream().anyMatch(call -> unusableDays.contains(call.day()))) {
                return EvalCheckVerdict.FAILED;
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
            boolean hasScore = scored != null && scored.hasWholeScore();
            if (AssessmentExpectation.expectsNoScore(label.getValue())) {
                if (hasScore) {
                    return EvalCheckVerdict.FAILED;
                }
            } else if (!hasScore || scored.wholeScore() < AssessmentExpectation.lowestOf(label.getValue())
                    || scored.wholeScore() > AssessmentExpectation.highestOf(label.getValue())) {
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
            if (selfReported.contains(scored.id()) && scored.hasWholeScore()
                    && scored.wholeScore() > SELF_REPORT_CEILING) {
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
        if (node.isString() || node.isNumber()) {
            into.append(node.asString()).append('\n');
        } else {
            node.forEach(child -> collectText(child, into));
        }
    }

    private static Set<String> daysOf(JsonNode dated) {
        Set<String> days = new HashSet<>();
        dated.forEach(entry -> days.add(EvidenceCitations.written(AssessmentCase.dayOf(entry))));
        return days;
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
