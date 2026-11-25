package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.enrichment.candidate.model.GoldenRow.GoldenProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * An eval case whose input is a profile alone, in the golden row's shape. Nothing but the profile's own fields is
 * let in, so a case can never put a contact, a salary or a note in front of the model.
 */
final class GoldenProfileCase {

    static final String PROFILE = "profile";

    private static final Set<String> PROFILE_FIELDS = Set.of("fullName", "title", "companyName", "locationCity",
            "locationCountry", "summary", "career", "education", "skills", "languages");

    private GoldenProfileCase() {
    }

    static List<String> problemsOf(JsonNode input, ObjectMapper json) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonNode> field : input.properties()) {
            if (!PROFILE.equals(field.getKey())) {
                problems.add("input holds something other than profile. A case of this prompt is a profile alone.");
                break;
            }
        }
        problems.addAll(profileProblemsOf(input.get(PROFILE), json));
        return problems;
    }

    /** What is wrong with the profile of a case, whatever else the case carries beside it. */
    static List<String> profileProblemsOf(JsonNode profile, ObjectMapper json) {
        List<String> problems = new ArrayList<>();
        if (profile == null || !profile.isObject()) {
            problems.add("input.profile is missing.");
            return problems;
        }
        for (Map.Entry<String, JsonNode> field : profile.properties()) {
            if (!PROFILE_FIELDS.contains(field.getKey())) {
                problems.add("input.profile holds a field a profile does not have. A case never carries a "
                        + "contact, a salary or a note.");
                break;
            }
        }
        JsonNode fullName = profile.get("fullName");
        if (fullName == null || !fullName.isString() || fullName.asString().isBlank()) {
            problems.add("input.profile.fullName is missing.");
        }
        try {
            json.treeToValue(profile, GoldenProfile.class);
        } catch (JacksonException unreadable) {
            problems.add("input.profile could not be read. Check that career, education, skills and languages "
                    + "are lists.");
        }
        return problems;
    }

    static GoldenProfile profileOf(JsonNode input, ObjectMapper json) {
        return json.treeToValue(input.get(PROFILE), GoldenProfile.class);
    }

    /** The label a case is scored on, or null when it is missing or not text. */
    static String labelOf(JsonNode expected, String label) {
        JsonNode value = expected == null ? null : expected.get(label);
        return value != null && value.isString() ? value.asString() : null;
    }
}
