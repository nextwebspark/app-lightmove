package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.CandidateEducationEntry;
import app.lightmove.api.core.llm.service.EvalCheck;
import app.lightmove.api.core.llm.service.EvalSubject;
import app.lightmove.api.outreach.model.OpenerBrief;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The opener's eval: a fictional profile and a few facts about a role, drafted by the drafter itself. An opener is
 * free text with no one right answer, so it has no checks: a judge compares the two versions' openers against the
 * dataset's rubric, and a case carries no label.
 */
@Component
@RequiredArgsConstructor
class OutreachOpenerEvalSubject implements EvalSubject {

    /** An opener is written differently on every call, so one answer says little. */
    static final int REPEATS = 3;

    private static final String PROFILE = "profile";
    private static final String ROLE = "role";
    private static final Set<String> PROFILE_FIELDS = Set.of("fullName", "title", "companyName", "locationCity",
            "locationCountry", "summary", "career", "education", "skills");
    private static final Set<String> ROLE_FIELDS =
            Set.of("title", "seniority", "sector", "locationCity", "locationCountry");

    private final OutreachOpenerDrafter drafter;
    private final ObjectMapper json;

    @Override
    public String promptId() {
        return "outreach-opener";
    }

    @Override
    public List<String> problemsOf(JsonNode input, JsonNode expected) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonNode> field : input.properties()) {
            if (!PROFILE.equals(field.getKey()) && !ROLE.equals(field.getKey())) {
                problems.add("input holds something other than profile and role.");
                break;
            }
        }
        problems.addAll(problemsOf(input.get(PROFILE), PROFILE, PROFILE_FIELDS, "fullName", CaseProfile.class));
        problems.addAll(problemsOf(input.get(ROLE), ROLE, ROLE_FIELDS, "title", CaseRole.class));
        if (expected != null && !expected.isNull()) {
            problems.add("expected must be left out. An opener has no label: a judge compares the answers.");
        }
        return problems;
    }

    @Override
    public void answer(JsonNode input) {
        CaseProfile profile = json.treeToValue(input.get(PROFILE), CaseProfile.class);
        drafter.draft(profile.dossier(), json.treeToValue(input.get(ROLE), CaseRole.class).brief());
    }

    @Override
    public List<EvalCheck> checks() {
        return List.of();
    }

    @Override
    public int minimumRepeats() {
        return REPEATS;
    }

    @Override
    public String proseOf(JsonNode answer) {
        JsonNode opener = answer == null ? null : answer.get("opener");
        return opener != null && opener.isString() && !opener.asString().isBlank() ? opener.asString() : null;
    }

    /** Pack v1 is drafted and waits for a person's review, as the assessment draft's does (#1326). */
    @Override
    public boolean isGateReady() {
        return false;
    }

    /** Nothing but the named fields is let in, so a case can never put a contact, a salary or a note in a prompt. */
    private List<String> problemsOf(JsonNode part, String name, Set<String> fields, String required,
                                    Class<?> shape) {
        if (part == null || !part.isObject()) {
            return List.of("input." + name + " is missing.");
        }
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonNode> field : part.properties()) {
            if (!fields.contains(field.getKey())) {
                problems.add("input." + name + " holds a field it does not have. A case never carries a contact, "
                        + "a salary, a note or the hiring company.");
                break;
            }
        }
        JsonNode named = part.get(required);
        if (named == null || !named.isString() || named.asString().isBlank()) {
            problems.add("input." + name + "." + required + " is missing.");
        }
        try {
            json.treeToValue(part, shape);
        } catch (JacksonException unreadable) {
            problems.add("input." + name + " could not be read. Check that its lists are lists and the rest text.");
        }
        return problems;
    }

    private record CaseProfile(String fullName, String title, String companyName, String locationCity,
                               String locationCountry, String summary, List<CandidateCareerEntry> career,
                               List<CandidateEducationEntry> education, List<String> skills) {

        CandidateDossier dossier() {
            return new CandidateDossier(fullName, title, companyName, locationCity, locationCountry, null, summary,
                    career == null ? List.of() : career, education == null ? List.of() : education,
                    skills == null ? List.of() : skills, List.of(), Set.of());
        }
    }

    private record CaseRole(String title, String seniority, String sector, String locationCity,
                            String locationCountry) {

        OpenerBrief brief() {
            return new OpenerBrief(title, seniority, sector, locationCity, locationCountry);
        }
    }
}
