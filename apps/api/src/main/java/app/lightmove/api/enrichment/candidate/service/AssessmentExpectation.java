package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.CandidateScorecard;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * What a consultant expects of an assessment draft on one case: for every competency of the case's brief a band the
 * score should fall in, or that nothing on file bears on it; and what must never come back at all.
 *
 * <pre>
 * {"competencies": {"T1": {"band": [7, 9], "restsOn": "call"},
 *                   "B1": {"band": [4, 7], "selfReportOnly": true},
 *                   "B2": {"band": null}},
 *  "neverInAnswer": ["a.person@example.com"], "notEvidence": ["ran three turnarounds"],
 *  "unusableCalls": ["2026-10-20"]}
 * </pre>
 */
final class AssessmentExpectation {

    static final String COMPETENCIES = "competencies";
    /** Contacts planted in the material, which redaction must keep from the model and the model from the answer. */
    static final String NEVER_IN_ANSWER = "neverInAnswer";
    /** Claims only a colleague, an unidentified speaker or an injected line makes: never the executive's evidence. */
    static final String NOT_EVIDENCE = "notEvidence";
    /** The dates of calls in which the executive cannot be told apart, so nothing may be cited from them. */
    static final String UNUSABLE_CALLS = "unusableCalls";

    static final int DEFAULT_TOLERANCE = 1;
    static final int MAX_TOLERANCE = 3;
    static final Set<String> SOURCES = Set.of(EvidenceCitations.PROFILE, EvidenceCitations.NOTE,
            EvidenceCitations.CALL, EvidenceCitations.DOCUMENT);

    private static final Set<String> FIELDS = Set.of(COMPETENCIES, NEVER_IN_ANSWER, NOT_EVIDENCE, UNUSABLE_CALLS);
    private static final Set<String> LABEL_FIELDS = Set.of("band", "tolerance", "restsOn", "selfReportOnly");

    private AssessmentExpectation() {
    }

    /** Asked only of a case whose input has no problem, so its brief can be read. */
    static List<String> problemsOf(JsonNode input, JsonNode expected) {
        List<String> problems = new ArrayList<>();
        if (expected == null || !expected.isObject()) {
            problems.add("expected is missing. An assessment case labels every competency of its brief.");
            return problems;
        }
        for (Map.Entry<String, JsonNode> field : expected.properties()) {
            if (!FIELDS.contains(field.getKey())) {
                problems.add("expected holds a field an assessment case does not have.");
                break;
            }
        }
        Set<String> briefIds = AssessmentCase.competenciesOf(input).keySet();
        JsonNode labels = expected.get(COMPETENCIES);
        if (labels == null || !labels.isObject()) {
            problems.add("expected.competencies is missing.");
        } else {
            for (Map.Entry<String, JsonNode> label : labels.properties()) {
                String at = "expected.competencies." + (briefIds.contains(label.getKey()) ? label.getKey() : "?");
                if (!briefIds.contains(label.getKey())) {
                    problems.add("expected.competencies names a competency the case's brief does not list.");
                } else {
                    labelProblems(label.getValue(), at, problems);
                }
            }
            for (String id : briefIds) {
                if (!labels.has(id)) {
                    problems.add("expected.competencies." + id + " is missing. Every competency of the brief is "
                            + "labelled.");
                }
            }
        }
        for (String list : List.of(NEVER_IN_ANSWER, NOT_EVIDENCE, UNUSABLE_CALLS)) {
            JsonNode values = expected.get(list);
            if (values != null && !values.isNull() && !isListOfText(values)) {
                problems.add("expected." + list + " is not a list of text.");
            }
        }
        Set<String> callDays = new HashSet<>();
        for (JsonNode transcript : input.path(AssessmentCase.TRANSCRIPTS)) {
            callDays.add(AssessmentCase.textOf(transcript, "date"));
        }
        for (String day : AssessmentCase.textsOf(expected, UNUSABLE_CALLS)) {
            if (!callDays.contains(day)) {
                problems.add("expected.unusableCalls names a date no call of the case has.");
                break;
            }
        }
        return problems;
    }

    static JsonNode labelOf(JsonNode expected, String competencyId) {
        return expected.path(COMPETENCIES).get(competencyId);
    }

    /** The label says nothing on file bears on the competency, so the score should be null. */
    static boolean expectsNoScore(JsonNode label) {
        return !label.path("band").isArray();
    }

    static int lowestOf(JsonNode label) {
        return label.get("band").get(0).asInt() - toleranceOf(label);
    }

    static int highestOf(JsonNode label) {
        return label.get("band").get(1).asInt() + toleranceOf(label);
    }

    static String restsOn(JsonNode label) {
        return AssessmentCase.textOf(label, "restsOn");
    }

    static boolean isSelfReportOnly(JsonNode label) {
        return label.path("selfReportOnly").asBoolean(false);
    }

    private static int toleranceOf(JsonNode label) {
        return label.path("tolerance").asInt(DEFAULT_TOLERANCE);
    }

    private static void labelProblems(JsonNode label, String at, List<String> problems) {
        if (label == null || !label.isObject()) {
            problems.add(at + " is not an object.");
            return;
        }
        for (Map.Entry<String, JsonNode> field : label.properties()) {
            if (!LABEL_FIELDS.contains(field.getKey())) {
                problems.add(at + " holds a field a label does not have.");
                break;
            }
        }
        JsonNode band = label.get("band");
        if (!label.has("band")) {
            problems.add(at + ".band is missing. Write a band as [6, 8], or null when nothing bears on it.");
        } else if (!band.isNull() && !isBand(band)) {
            problems.add(at + ".band is not two whole numbers from 0 to 10, the lower first.");
        }
        JsonNode tolerance = label.get("tolerance");
        if (tolerance != null && !(tolerance.isIntegralNumber() && tolerance.asInt() >= 0
                && tolerance.asInt() <= MAX_TOLERANCE)) {
            problems.add(at + ".tolerance is not a whole number from 0 to " + MAX_TOLERANCE + ".");
        }
        JsonNode restsOn = label.get("restsOn");
        if (restsOn != null && !(restsOn.isString() && SOURCES.contains(restsOn.asString()))) {
            problems.add(at + ".restsOn is not profile, note, call or document.");
        }
        JsonNode selfReport = label.get("selfReportOnly");
        if (selfReport != null && !selfReport.isBoolean()) {
            problems.add(at + ".selfReportOnly is not true or false.");
        }
    }

    private static boolean isBand(JsonNode band) {
        return band.isArray() && band.size() == 2 && isScore(band.get(0)) && isScore(band.get(1))
                && band.get(0).asInt() <= band.get(1).asInt();
    }

    private static boolean isScore(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToInt() && value.asInt() >= CandidateScorecard.MIN_SCORE
                && value.asInt() <= CandidateScorecard.MAX_SCORE;
    }

    private static boolean isListOfText(JsonNode values) {
        if (!values.isArray()) {
            return false;
        }
        for (JsonNode value : values) {
            if (!value.isString()) {
                return false;
            }
        }
        return true;
    }
}
